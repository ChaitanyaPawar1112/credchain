/**
 * Live end-to-end demo of CredentialRegistry on a real network.
 *
 *   npx hardhat run scripts/demo-sepolia.ts --network sepolia
 *
 * Flow: approve issuer -> issue -> verify -> detect tampering -> revoke -> Merkle batch.
 * The contract address is read from the Ignition deployment record for the current chain.
 * Certificate data is fictional; only keccak256 hashes are written on-chain.
 */
import { network } from "hardhat";
import { StandardMerkleTree } from "@openzeppelin/merkle-tree";
import type { ContractTransactionResponse } from "ethers";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const STATUS = ["NOT_FOUND", "VALID", "REVOKED", "EXPIRED"] as const;
const REASON_ISSUED_IN_ERROR = 1;
const NO_EXPIRY = 0n;
const EXPLORERS: Record<string, string> = {
    "11155111": "https://sepolia.etherscan.io",
};

const { ethers, networkName } = await network.getOrCreate();
const { chainId } = await ethers.provider.getNetwork();
const explorer = EXPLORERS[chainId.toString()];
const txLink = (hash: string) => (explorer ? `${explorer}/tx/${hash}` : hash);

// ---------- locate the deployed contract ----------
const addressesFile = path.join("ignition", "deployments", `chain-${chainId}`, "deployed_addresses.json");
const addresses = JSON.parse(await readFile(addressesFile, "utf8")) as Record<string, string>;
const registryAddress = addresses["CredentialRegistryModule#CredentialRegistry"];
if (!registryAddress) {
    throw new Error(`No CredentialRegistry deployment found in ${addressesFile}`);
}

const [admin] = await ethers.getSigners();
const registry = await ethers.getContractAt("CredentialRegistry", registryAddress, admin);

// ---------- helpers ----------
const runId = new Date().toISOString();

function certificateHash(certificate: object): string {
    return ethers.keccak256(ethers.toUtf8Bytes(JSON.stringify(certificate)));
}

function demoCertificate(enrollmentNo: string, fullName: string, cgpa: string) {
    return {
        institution: "DEMO-INSTITUTE",
        enrollmentNo,
        fullName,
        program: "B.Tech Computer Science",
        graduationYear: 2026,
        cgpa,
        runId, // makes every demo run unique
    };
}

async function send(label: string, txPromise: Promise<ContractTransactionResponse>): Promise<string> {
    const tx = await txPromise;
    console.log(`   -> ${label}: sent, waiting for confirmation...`);
    const receipt = await tx.wait();
    console.log(`      confirmed in block ${receipt?.blockNumber}, gas used ${receipt?.gasUsed}`);
    console.log(`      ${txLink(tx.hash)}`);
    return tx.hash;
}

async function statusOf(certHash: string): Promise<string> {
    const [status] = await registry.verify(certHash);
    return STATUS[Number(status)];
}

const transactions: Record<string, string> = {};

// ---------- 0. context ----------
console.log("\n=== CredChain live demo ===");
console.log(`Network:   ${networkName} (chain ${chainId})`);
console.log(`Contract:  ${registryAddress}`);
console.log(`Wallet:    ${admin.address}`);
console.log(`Balance:   ${ethers.formatEther(await ethers.provider.getBalance(admin.address))} ETH`);
console.log(`Version:   ${await registry.VERSION()}`);

// ---------- 1. approve the issuer ----------
console.log("\n[1] Approve institution wallet as issuer (admin)");
if (await registry.isIssuer(admin.address)) {
    console.log("   already an issuer, skipping");
} else {
    transactions.addIssuer = await send("addIssuer", registry.addIssuer(admin.address));
}

// ---------- 2. issue + verify ----------
console.log("\n[2] Issue certificate A and verify it");
const certA = demoCertificate("DEMO-001", "Demo Student A", "8.75");
const hashA = certificateHash(certA);
console.log(`   certificate A hash: ${hashA}`);
transactions.issueA = await send("issueCredential(A)", registry.issueCredential(hashA, NO_EXPIRY));
console.log(`   verify(A) = ${await statusOf(hashA)}   (expected VALID)`);

// ---------- 3. tampering ----------
console.log("\n[3] Employer checks a tampered copy (CGPA edited 8.75 -> 9.75)");
const tamperedHash = certificateHash({ ...certA, cgpa: "9.75" });
console.log(`   tampered hash:      ${tamperedHash}`);
console.log(`   verify(tampered) = ${await statusOf(tamperedHash)}   (expected NOT_FOUND)`);

// ---------- 4. issue + revoke ----------
console.log("\n[4] Issue certificate B, then revoke it");
const certB = demoCertificate("DEMO-002", "Demo Student B", "7.10");
const hashB = certificateHash(certB);
transactions.issueB = await send("issueCredential(B)", registry.issueCredential(hashB, NO_EXPIRY));
transactions.revokeB = await send(
    "revokeCredential(B, ISSUED_IN_ERROR)",
    registry.revokeCredential(hashB, REASON_ISSUED_IN_ERROR),
);
console.log(`   verify(B) = ${await statusOf(hashB)}   (expected REVOKED)`);

// ---------- 5. Merkle batch ----------
console.log("\n[5] Issue a batch of 3 certificates with ONE transaction (Merkle root)");
const batchCerts = [
    demoCertificate("DEMO-101", "Demo Graduate 1", "8.10"),
    demoCertificate("DEMO-102", "Demo Graduate 2", "9.02"),
    demoCertificate("DEMO-103", "Demo Graduate 3", "7.66"),
];
const batchHashes = batchCerts.map(certificateHash);
const tree = StandardMerkleTree.of(batchHashes.map((h) => [h]), ["bytes32"]);
console.log(`   Merkle root: ${tree.root}`);
transactions.issueBatch = await send(
    "issueBatch(root, 3)",
    registry.issueBatch(tree.root, batchHashes.length, NO_EXPIRY),
);

const proofs: string[][] = [];
for (const [i, h] of batchHashes.entries()) {
    const proof = tree.getProof(i);
    proofs.push(proof);
    const [status] = await registry.verifyInBatch(tree.root, h, proof);
    console.log(`   verifyInBatch(graduate ${i + 1}) = ${STATUS[Number(status)]}   (expected VALID)`);
}

// ---------- save results (public data only) ----------
const outDir = "demo";
const outFile = path.join(outDir, `demo-result-chain-${chainId}.json`);
await mkdir(outDir, { recursive: true });
await writeFile(
    outFile,
    JSON.stringify(
        {
            runId,
            chainId: chainId.toString(),
            contract: registryAddress,
            wallet: admin.address,
            transactions,
            single: {
                certificateA: { data: certA, hash: hashA, expected: "VALID" },
                tamperedA: { hash: tamperedHash, expected: "NOT_FOUND" },
                certificateB: { data: certB, hash: hashB, expected: "REVOKED" },
            },
            batch: {
                root: tree.root,
                entries: batchCerts.map((data, i) => ({ data, hash: batchHashes[i], proof: proofs[i] })),
            },
        },
        null,
        2,
    ),
);

console.log(`\nSaved hashes, root, proofs and tx hashes to ${outFile}`);
console.log(`Balance now: ${ethers.formatEther(await ethers.provider.getBalance(admin.address))} ETH`);
if (explorer) {
    console.log(`All transactions: ${explorer}/address/${registryAddress}`);
}
console.log("=== demo finished ===\n");