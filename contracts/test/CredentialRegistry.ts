import { expect } from "chai";
import { network } from "hardhat";
import { StandardMerkleTree } from "@openzeppelin/merkle-tree";

const { ethers, networkHelpers } = await network.getOrCreate();
const { loadFixture, time } = networkHelpers;

// Status and RevocationReason enums, as returned by the contract (uint8 -> bigint)
const STATUS = { NOT_FOUND: 0n, VALID: 1n, REVOKED: 2n, EXPIRED: 3n };
const REASON = { UNSPECIFIED: 0n, ISSUED_IN_ERROR: 1n, FRAUD: 2n, SUPERSEDED: 3n, OTHER: 4n };

/** SHA-256 of a text, standing in for the SHA-256 of a certificate PDF. */
const certHash = (text: string) => ethers.sha256(ethers.toUtf8Bytes(text));

async function deployRegistryFixture() {
    const [admin, issuer, otherIssuer, stranger] = await ethers.getSigners();
    const registry = await ethers.deployContract("CredentialRegistry", [admin.address]);
    await registry.addIssuer(issuer.address);
    await registry.addIssuer(otherIssuer.address);
    const ISSUER_ROLE = await registry.ISSUER_ROLE();
    const DEFAULT_ADMIN_ROLE = await registry.DEFAULT_ADMIN_ROLE();
    return { registry, admin, issuer, otherIssuer, stranger, ISSUER_ROLE, DEFAULT_ADMIN_ROLE };
}

describe("CredentialRegistry", function () {

    describe("deployment", function () {
        it("gives the admin role to the given address and reports its version", async function () {
            const { registry, admin, DEFAULT_ADMIN_ROLE } = await loadFixture(deployRegistryFixture);
            expect(await registry.hasRole(DEFAULT_ADMIN_ROLE, admin.address)).to.equal(true);
            expect(await registry.VERSION()).to.equal("1.0.0");
        });


        it("rejects a zero admin address", async function () {
            const factory = await ethers.getContractFactory("CredentialRegistry");
            await expect(factory.deploy(ethers.ZeroAddress))
                .to.be.revertedWithCustomError(factory, "ZeroAddress");
        });
    });

    describe("issuer management", function () {
        it("only the admin can add an issuer", async function () {
            const { registry, stranger, DEFAULT_ADMIN_ROLE } = await loadFixture(deployRegistryFixture);
            await expect(registry.connect(stranger).addIssuer(stranger.address))
                .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
                .withArgs(stranger.address, DEFAULT_ADMIN_ROLE);
        });

        it("a removed issuer can no longer issue", async function () {
            const { registry, issuer, ISSUER_ROLE } = await loadFixture(deployRegistryFixture);
            expect(await registry.isIssuer(issuer.address)).to.equal(true);

            await registry.removeIssuer(issuer.address);

            expect(await registry.isIssuer(issuer.address)).to.equal(false);
            await expect(registry.connect(issuer).issueCredential(certHash("c1"), 0))
                .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
                .withArgs(issuer.address, ISSUER_ROLE);
        });
    });

    describe("issuing a single credential", function () {
        it("stores it, emits an event and verifies as VALID", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);

            const h = certHash("SIT-AUR/2022CS001/B.Tech");

            await expect(registry.connect(issuer).issueCredential(h, 0))
                .to.emit(registry, "CredentialIssued")
                .withArgs(h, issuer.address, 0n);

            const result = await registry.verify(h);
            expect(result.status).to.equal(STATUS.VALID);
            expect(result.issuer).to.equal(issuer.address);
            expect(result.issuedAt).to.be.greaterThan(0n);
        });

        it("returns NOT_FOUND for an unknown hash (e.g. a forged certificate)", async function () {
            const { registry } = await loadFixture(deployRegistryFixture);
            const result = await registry.verify(certHash("forged"));
            expect(result.status).to.equal(STATUS.NOT_FOUND);
            expect(result.issuer).to.equal(ethers.ZeroAddress);
        });

        it("rejects callers without ISSUER_ROLE", async function () {
            const { registry, stranger, ISSUER_ROLE } = await loadFixture(deployRegistryFixture);
            await expect(registry.connect(stranger).issueCredential(certHash("c1"), 0))
                .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount")
                .withArgs(stranger.address, ISSUER_ROLE);
        });

        it("rejects duplicates, the zero hash and an expiry in the past", async function () {
            const { registry, issuer, otherIssuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("c1");
            await registry.connect(issuer).issueCredential(h, 0);

            await expect(registry.connect(otherIssuer).issueCredential(h, 0))

                .to.be.revertedWithCustomError(registry, "AlreadyIssued").withArgs(h);
            await expect(registry.connect(issuer).issueCredential(ethers.ZeroHash, 0))
                .to.be.revertedWithCustomError(registry, "ZeroHash");

            const past = (await time.latest()) - 10;
            await expect(registry.connect(issuer).issueCredential(certHash("c2"), past))
                .to.be.revertedWithCustomError(registry, "InvalidExpiry").withArgs(past);
        });
    });

    describe("expiry and revocation", function () {
        it("becomes EXPIRED once the expiry time passes", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("short-course");
            const expiresAt = (await time.latest()) + 3600;
            await registry.connect(issuer).issueCredential(h, expiresAt);

            expect((await registry.verify(h)).status).to.equal(STATUS.VALID);
            await time.increaseTo(expiresAt);
            expect((await registry.verify(h)).status).to.equal(STATUS.EXPIRED);
        });

        it("the issuing institution can revoke, and REVOKED beats EXPIRED", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("c1");
            const expiresAt = (await time.latest()) + 3600;
            await registry.connect(issuer).issueCredential(h, expiresAt);

            await expect(registry.connect(issuer).revokeCredential(h, REASON.ISSUED_IN_ERROR))
                .to.emit(registry, "CredentialRevoked")
                .withArgs(h, issuer.address, REASON.ISSUED_IN_ERROR);


            expect((await registry.verify(h)).status).to.equal(STATUS.REVOKED);
            await time.increaseTo(expiresAt + 1);
            expect((await registry.verify(h)).status).to.equal(STATUS.REVOKED);
        });

        it("another institution cannot revoke it, but the platform admin can (fraud)", async function () {
            const { registry, admin, issuer, otherIssuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("c1");
            await registry.connect(issuer).issueCredential(h, 0);

            await expect(registry.connect(otherIssuer).revokeCredential(h, REASON.FRAUD))
                .to.be.revertedWithCustomError(registry, "NotAuthorizedToRevoke").withArgs(otherIssuer.address);

            await expect(registry.connect(admin).revokeCredential(h, REASON.FRAUD))
                .to.emit(registry, "CredentialRevoked").withArgs(h, admin.address, REASON.FRAUD);
        });

        it("cannot revoke twice or revoke an unknown credential", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("c1");
            await registry.connect(issuer).issueCredential(h, 0);
            await registry.connect(issuer).revokeCredential(h, REASON.OTHER);

            await expect(registry.connect(issuer).revokeCredential(h, REASON.OTHER))
                .to.be.revertedWithCustomError(registry, "AlreadyRevoked").withArgs(h);
            const unknown = certHash("unknown");
            await expect(registry.connect(issuer).revokeCredential(unknown, REASON.OTHER))
                .to.be.revertedWithCustomError(registry, "NotFound").withArgs(unknown);
        });

        it("a removed institution can still revoke its own past credentials", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);

            const h = certHash("c1");
            await registry.connect(issuer).issueCredential(h, 0);
            await registry.removeIssuer(issuer.address);

            await registry.connect(issuer).revokeCredential(h, REASON.SUPERSEDED);
            expect((await registry.verify(h)).status).to.equal(STATUS.REVOKED);
        });
    });

    describe("pause (emergency stop)", function () {
        it("blocks issuing but still allows revocation", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const h = certHash("c1");
            await registry.connect(issuer).issueCredential(h, 0);

            await registry.pause();
            await expect(registry.connect(issuer).issueCredential(certHash("c2"), 0))
                .to.be.revertedWithCustomError(registry, "EnforcedPause");
            await registry.connect(issuer).revokeCredential(h, REASON.FRAUD);

            await registry.unpause();
            await registry.connect(issuer).issueCredential(certHash("c2"), 0);
        });
    });

    describe("Merkle batches", function () {
        const hashes = ["2022CS001", "2022CS002", "2022CS003", "2022IT014", "2022IT015"]
            .map((no) => certHash(`SIT-AUR/${no}/B.Tech/2026`));

        function buildTree() {
            return StandardMerkleTree.of(hashes.map((h) => [h]), ["bytes32"]);
        }


        it("on-chain leafOf() matches OpenZeppelin's off-chain Merkle library", async function () {
            const { registry } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();
            for (const h of hashes) {
                expect(await registry.leafOf(h)).to.equal(tree.leafHash([h]));
            }
        });

        it("issues a batch in one transaction and verifies every member", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();

            await expect(registry.connect(issuer).issueBatch(tree.root, hashes.length, 0))
                .to.emit(registry, "BatchIssued")
                .withArgs(tree.root, issuer.address, BigInt(hashes.length), 0n);

            for (const h of hashes) {
                const result = await registry.verifyInBatch(tree.root, h, tree.getProof([h]));
                expect(result.status).to.equal(STATUS.VALID);
                expect(result.issuer).to.equal(issuer.address);
            }
        });

        it("rejects a tampered certificate or a proof for a different certificate", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();
            await registry.connect(issuer).issueBatch(tree.root, hashes.length, 0);

            const tampered = certHash("SIT-AUR/2022CS001/B.Tech/2026 (edited)");
            const proofOfFirst = tree.getProof([hashes[0]]);
            expect((await registry.verifyInBatch(tree.root, tampered, proofOfFirst)).status)

                .to.equal(STATUS.NOT_FOUND);
            expect((await registry.verifyInBatch(tree.root, hashes[1], proofOfFirst)).status)
                .to.equal(STATUS.NOT_FOUND);
        });

        it("revokes ONE entry while the rest of the batch stays valid", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();
            await registry.connect(issuer).issueBatch(tree.root, hashes.length, 0);

            await expect(registry.connect(issuer)
                .revokeBatchEntry(tree.root, hashes[2], tree.getProof([hashes[2]]), REASON.FRAUD))
                .to.emit(registry, "BatchEntryRevoked")
                .withArgs(tree.root, hashes[2], issuer.address, REASON.FRAUD);

            expect((await registry.verifyInBatch(tree.root, hashes[2], tree.getProof([hashes[2]]))).status)
                .to.equal(STATUS.REVOKED);
            expect((await registry.verifyInBatch(tree.root, hashes[0], tree.getProof([hashes[0]]))).status)
                .to.equal(STATUS.VALID);

            await expect(registry.connect(issuer)
                .revokeBatchEntry(tree.root, hashes[2], tree.getProof([hashes[0]]), REASON.FRAUD))
                .to.be.revertedWithCustomError(registry, "InvalidProof");
        });

        it("revoking the whole batch revokes every member", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();
            await registry.connect(issuer).issueBatch(tree.root, hashes.length, 0);

            await registry.connect(issuer).revokeBatch(tree.root, REASON.ISSUED_IN_ERROR);


            for (const h of hashes) {
                expect((await registry.verifyInBatch(tree.root, h, tree.getProof([h]))).status)
                    .to.equal(STATUS.REVOKED);
            }
        });

        it("rejects an empty batch and a duplicate root", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);
            const tree = buildTree();
            await expect(registry.connect(issuer).issueBatch(tree.root, 0, 0))
                .to.be.revertedWithCustomError(registry, "EmptyBatch");

            await registry.connect(issuer).issueBatch(tree.root, hashes.length, 0);
            await expect(registry.connect(issuer).issueBatch(tree.root, hashes.length, 0))
                .to.be.revertedWithCustomError(registry, "AlreadyIssued").withArgs(tree.root);
        });
    });

    describe("gas cost", function () {
        it("one batch of 100 certificates costs far less than 100 single issues", async function () {
            const { registry, issuer } = await loadFixture(deployRegistryFixture);

            const singleTx = await registry.connect(issuer).issueCredential(certHash("single"), 0);
            const singleGas = (await singleTx.wait())!.gasUsed;

            const many = Array.from({ length: 100 }, (_, i) => [certHash(`batch-${i}`)]);
            const tree = StandardMerkleTree.of(many, ["bytes32"]);
            const batchTx = await registry.connect(issuer).issueBatch(tree.root, 100, 0);
            const batchGas = (await batchTx.wait())!.gasUsed;

            const hundredSingles = singleGas * 100n;
            const savedPercent = Number(((hundredSingles - batchGas) * 10000n) / hundredSingles) / 100;

            console.log(`      single issue: ${singleGas} gas | 100 singles: ${hundredSingles} gas | `
                + `1 batch of 100: ${batchGas} gas | saved ${savedPercent}%`);

            expect(batchGas * 10n).to.be.lessThan(hundredSingles);
        });
    });
});