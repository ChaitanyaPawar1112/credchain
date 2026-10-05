import { readFileSync } from "node:fs";
import { network } from "hardhat";

const { ethers } = await network.getOrCreate();

const chainId = (await ethers.provider.getNetwork()).chainId;
const deployed = JSON.parse(
    readFileSync(`ignition/deployments/chain-${chainId}/deployed_addresses.json`, "utf8"));
const address: string = deployed["CredentialRegistryModule#CredentialRegistry"];
const registry = await ethers.getContractAt("CredentialRegistry", address);

const [admin, institution, employer] = await ethers.getSigners();
const STATUS = ["NOT_FOUND", "VALID", "REVOKED", "EXPIRED"];

console.log(`Registry ${address} on chain ${chainId}`);
console.log(`Admin       ${admin.address}`);
console.log(`Institution ${institution.address}`);
console.log(`Employer    ${employer.address}\n`);

// 1. Platform admin approves the institution's wallet (mirrors Phase 2 approval)
if (!(await registry.isIssuer(institution.address))) {
    await (await registry.addIssuer(institution.address)).wait();
    console.log("1. Admin added institution as issuer");
} else {
    console.log("1. Institution is already an issuer");
}

// 2. Institution issues a certificate (only its SHA-256 goes on-chain)
const certHash = ethers.sha256(ethers.toUtf8Bytes(`SIT-AUR/2022CS001/B.Tech/${Date.now()}`));
const receipt = await (await registry.connect(institution).issueCredential(certHash, 0)).wait();
console.log(`2. Issued ${certHash}`);
console.log(`   tx ${receipt!.hash} | block ${receipt!.blockNumber} | gas ${receipt!.gasUsed}`);


// 3. Employer verifies (a free read, no transaction)
const genuine = await registry.connect(employer).verify(certHash);
console.log(`3. Employer verifies genuine certificate -> ${STATUS[Number(genuine.status)]}, issuer ${genuine.issuer}`);

const forged = await registry.connect(employer).verify(ethers.sha256(ethers.toUtf8Bytes("edited certificate")));
console.log(`4. Employer verifies forged certificate  -> ${STATUS[Number(forged.status)]}`);