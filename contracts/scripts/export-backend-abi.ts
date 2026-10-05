/**
 * Exports the deployed CredentialRegistry ABI, bytecode and addresses for the Java backend (web3j).
 *
 *   npx hardhat run scripts/export-backend-abi.ts
 *
 * Source of truth = Ignition deployment records (exactly what is on-chain), not the local build.
 * Local Hardhat deployments (chain 31337) are skipped because they disappear on restart.
 * Output: backend/src/main/resources/blockchain/
 */
import { existsSync } from "node:fs";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";

const FUTURE_ID = "CredentialRegistryModule#CredentialRegistry";
const DEPLOYMENTS_DIR = path.join("ignition", "deployments");
const OUT_DIR = path.join("..", "backend", "src", "main", "resources", "blockchain");
const SKIP_CHAINS = new Set(["31337"]);
const NETWORKS: Record<string, { name: string; explorer: string }> = {
    "1": { name: "mainnet", explorer: "https://etherscan.io" },
    "11155111": { name: "sepolia", explorer: "https://sepolia.etherscan.io" },
};

interface Artifact {
    contractName: string;
    abi: unknown[];
    bytecode: string;
}

interface DeploymentInfo {
    network: string;
    chainId: number;
    address: string;
    deploymentTx: string | null;
    deploymentBlock: number | null;
    explorer: string | null;
}

/** Finds the confirmed deploy transaction (hash + block) in Ignition's journal. */
async function readDeploymentTx(chainDir: string): Promise<{ hash: string | null; block: number | null }> {
    const journalFile = path.join(chainDir, "journal.jsonl");
    if (!existsSync(journalFile)) return { hash: null, block: null };

    const lines = (await readFile(journalFile, "utf8")).split(/\r?\n/).filter(Boolean);
    for (const line of lines) {
        const entry = JSON.parse(line);
        if (entry.type === "TRANSACTION_CONFIRM" && entry.futureId === FUTURE_ID) {
            return { hash: entry.hash ?? null, block: entry.receipt?.blockNumber ?? null };
        }
    }
    return { hash: null, block: null };
}

async function readContractVersion(): Promise<string | null> {
    const source = await readFile(path.join("contracts", "CredentialRegistry.sol"), "utf8");
    return source.match(/VERSION\s*=\s*"([^"]+)"/)?.[1] ?? null;
}

// ---------- collect deployments ----------
const chainDirs = (await readdir(DEPLOYMENTS_DIR, { withFileTypes: true }))
    .filter((d) => d.isDirectory() && d.name.startsWith("chain-"))
    .map((d) => d.name)
    .filter((name) => !SKIP_CHAINS.has(name.replace("chain-", "")))
    .sort();

if (chainDirs.length === 0) {
    throw new Error(`No public deployments found in ${DEPLOYMENTS_DIR}`);
}

const deployments: Record<string, DeploymentInfo> = {};
let artifact: Artifact | null = null;

for (const dirName of chainDirs) {
    const chainId = dirName.replace("chain-", "");
    const chainDir = path.join(DEPLOYMENTS_DIR, dirName);

    const addresses = JSON.parse(await readFile(path.join(chainDir, "deployed_addresses.json"), "utf8"));
    const address: string | undefined = addresses[FUTURE_ID];
    if (!address) continue;

    const chainArtifact: Artifact = JSON.parse(
        await readFile(path.join(chainDir, "artifacts", `${FUTURE_ID}.json`), "utf8"),
    );
    if (artifact && JSON.stringify(artifact.abi) !== JSON.stringify(chainArtifact.abi)) {
        throw new Error(`ABI on chain ${chainId} differs from another deployment; export them separately`);
    }
    artifact = chainArtifact;

    const tx = await readDeploymentTx(chainDir);
    const net = NETWORKS[chainId];
    deployments[chainId] = {
        network: net?.name ?? `chain-${chainId}`,
        chainId: Number(chainId),
        address,
        deploymentTx: tx.hash,
        deploymentBlock: tx.block,
        explorer: net ? `${net.explorer}/address/${address}` : null,
    };
}

if (!artifact) {
    throw new Error(`No ${FUTURE_ID} deployment found in ${chainDirs.join(", ")}`);
}

// ---------- write files for the backend ----------
await mkdir(OUT_DIR, { recursive: true });

const abiFile = path.join(OUT_DIR, `${artifact.contractName}.abi`);
const binFile = path.join(OUT_DIR, `${artifact.contractName}.bin`);
const deploymentsFile = path.join(OUT_DIR, "credential-registry-deployments.json");

await writeFile(abiFile, JSON.stringify(artifact.abi, null, 2) + "\n");
await writeFile(binFile, artifact.bytecode.replace(/^0x/, "") + "\n");
await writeFile(
    deploymentsFile,
    JSON.stringify(
        {
            contract: artifact.contractName,
            version: await readContractVersion(),
            exportedAt: new Date().toISOString(),
            deployments,
        },
        null,
        2,
    ) + "\n",
);

console.log("Exported for backend (web3j):");
console.log(`  ${abiFile}`);
console.log(`  ${binFile}`);
console.log(`  ${deploymentsFile}`);
for (const d of Object.values(deployments)) {
    console.log(`  - ${d.network} (${d.chainId}): ${d.address}  block ${d.deploymentBlock ?? "?"}`);
}