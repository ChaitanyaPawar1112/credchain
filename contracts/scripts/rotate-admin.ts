/**
 * Admin key rotation for CredentialRegistry.
 *
 *   npx hardhat run scripts/rotate-admin.ts --network sepolia
 *
 * Runs with the CURRENT admin key (SEPOLIA_PRIVATE_KEY in the keystore) and:
 *   1. grants DEFAULT_ADMIN_ROLE to NEW_ADMIN
 *   2. verifies the grant before doing anything irreversible
 *   3. removes the old wallet's ISSUER_ROLE
 *   4. renounces the old wallet's DEFAULT_ADMIN_ROLE
 *   5. moves the remaining ETH to NEW_ADMIN
 * Safe to re-run: completed steps are skipped.
 */
import { network } from "hardhat";
import type { TransactionResponse } from "ethers";
import { readFile } from "node:fs/promises";
import path from "node:path";

const NEW_ADMIN = "0x09941292AF95DA3dedd9849B077eaC1B1142AB69";
const TRANSFER_GAS = 21_000n;

const { ethers, networkName } = await network.getOrCreate();
const { chainId } = await ethers.provider.getNetwork();

const addressesFile = path.join("ignition", "deployments", `chain-${chainId}`, "deployed_addresses.json");
const addresses = JSON.parse(await readFile(addressesFile, "utf8")) as Record<string, string>;
const registryAddress = addresses["CredentialRegistryModule#CredentialRegistry"];
if (!registryAddress) throw new Error(`No CredentialRegistry deployment in ${addressesFile}`);

const [oldAdmin] = await ethers.getSigners();
const newAdmin = ethers.getAddress(NEW_ADMIN); // throws if the address is malformed
if (newAdmin === oldAdmin.address) throw new Error("NEW_ADMIN is the same as the current signer");

const registry = await ethers.getContractAt("CredentialRegistry", registryAddress, oldAdmin);
const ADMIN_ROLE = await registry.DEFAULT_ADMIN_ROLE();

async function send(label: string, txPromise: Promise<TransactionResponse>): Promise<void> {
    const tx = await txPromise;
    console.log(`   -> ${label}: sent, waiting for confirmation...`);
    const receipt = await tx.wait();
    console.log(`      confirmed in block ${receipt?.blockNumber}`);
    console.log(`      https://sepolia.etherscan.io/tx/${tx.hash}`);
}

const eth = async (address: string) => ethers.formatEther(await ethers.provider.getBalance(address));

console.log("\n=== CredentialRegistry admin rotation ===");
console.log(`Network:   ${networkName} (chain ${chainId})`);
console.log(`Contract:  ${registryAddress}`);
console.log(`Old admin: ${oldAdmin.address}  (${await eth(oldAdmin.address)} ETH)`);
console.log(`New admin: ${newAdmin}  (${await eth(newAdmin)} ETH)`);

// ---------- roles ----------
if (await registry.hasRole(ADMIN_ROLE, oldAdmin.address)) {
    console.log("\n[1] Grant admin role to the new wallet");
    if (await registry.hasRole(ADMIN_ROLE, newAdmin)) {
        console.log("   already admin, skipping");
    } else {
        await send("grantRole(DEFAULT_ADMIN_ROLE, new)", registry.grantRole(ADMIN_ROLE, newAdmin));
    }

    console.log("\n[2] Safety check");
    if (!(await registry.hasRole(ADMIN_ROLE, newAdmin))) {
        throw new Error("New wallet does NOT have the admin role. Aborting before anything irreversible.");
    }
    console.log("   new wallet is admin: OK");

    console.log("\n[3] Remove the old wallet's issuer role");
    if (await registry.isIssuer(oldAdmin.address)) {
        await send("removeIssuer(old)", registry.removeIssuer(oldAdmin.address));
    } else {
        console.log("   old wallet is not an issuer, skipping");
    }

    console.log("\n[4] Old wallet renounces the admin role");
    await send("renounceRole(DEFAULT_ADMIN_ROLE, old)", registry.renounceRole(ADMIN_ROLE, oldAdmin.address));
} else {
    console.log("\nOld wallet is no longer admin: role steps already done, skipping");
}

// ---------- funds ----------
console.log("\n[5] Move remaining ETH to the new wallet");
const balance = await ethers.provider.getBalance(oldAdmin.address);
const fees = await ethers.provider.getFeeData();
const maxFeePerGas = fees.maxFeePerGas ?? fees.gasPrice;
if (maxFeePerGas === null) throw new Error("Could not read the network fee");
const reserve = TRANSFER_GAS * maxFeePerGas * 2n; // fee + safety margin, left behind as dust
const value = balance - reserve;

if (value > 0n) {
    await send(
        `transfer ${ethers.formatEther(value)} ETH`,
        oldAdmin.sendTransaction({
            to: newAdmin,
            value,
            gasLimit: TRANSFER_GAS,
            maxFeePerGas,
            maxPriorityFeePerGas: fees.maxPriorityFeePerGas ?? undefined,
        }),
    );
} else {
    console.log("   nothing worth moving, skipping");
}

// ---------- final state ----------
console.log("\n=== Result ===");
console.log(`new wallet is admin:   ${await registry.hasRole(ADMIN_ROLE, newAdmin)}   (expected true)`);
console.log(`old wallet is admin:   ${await registry.hasRole(ADMIN_ROLE, oldAdmin.address)}  (expected false)`);
console.log(`old wallet is issuer:  ${await registry.isIssuer(oldAdmin.address)}  (expected false)`);
console.log(`old wallet balance:    ${await eth(oldAdmin.address)} ETH (dust only)`);
console.log(`new wallet balance:    ${await eth(newAdmin)} ETH`);
console.log("Next: put the NEW wallet's private key in the keystore and infra/.env\n");