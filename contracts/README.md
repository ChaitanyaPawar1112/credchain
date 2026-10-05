# CredChain – Smart Contracts

On-chain trust layer for **CredChain – Blockchain-Based Academic Credential Verification System**.

`CredentialRegistry` stores a **keccak256 fingerprint** of every certificate issued by an approved
institution. Anyone can verify a certificate for free, without trusting the CredChain server.
No personal data is ever written on-chain (privacy by design, DPDP Act 2023).

## Live deployment

| Network | Chain ID | Address | Source |
|---------|----------|---------|--------|
| Ethereum Sepolia (testnet) | 11155111 | [`0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0`](https://sepolia.etherscan.io/address/0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0#code) | Verified (exact match) on Etherscan, Blockscout and Sourcify |

- Deployed in block `11846277` with Hardhat Ignition (record: `ignition/deployments/chain-11155111/`)
- Admin (DEFAULT_ADMIN_ROLE): `0xdB254B2fE0fE4E6f6BE86fE9f6765daBc3c40038`
- Live demo transactions: `demo/demo-result-chain-11155111.json`

## Contract design

| Concept | Implementation |
|---------|----------------|
| Roles | OpenZeppelin `AccessControl`: `DEFAULT_ADMIN_ROLE` (platform), `ISSUER_ROLE` (approved institution wallets) |
| Single issue | `issueCredential(certHash, expiresAt)` – `expiresAt = 0` means never expires |
| Batch issue | `issueBatch(merkleRoot, count, expiresAt)` – one transaction for a whole convocation |
| Verify (free) | `verify(certHash)`, `verifyInBatch(root, certHash, proof)` → `NOT_FOUND / VALID / REVOKED / EXPIRED` |
| Revoke | `revokeCredential`, `revokeBatch`, `revokeBatchEntry` – only the original issuer or the admin |
| Emergency | `pause()` blocks issuing; verify and revoke keep working |
| Merkle leaves | `keccak256(bytes.concat(keccak256(abi.encode(certHash))))` – compatible with `@openzeppelin/merkle-tree` `StandardMerkleTree` |

Status rules: never issued → `NOT_FOUND`; revoked always wins over expired; records are never deleted.

### Gas (measured)

| Action | Gas |
|--------|-----|
| Deploy | 1,300,438 |
| `addIssuer` | ~48,850 |
| `issueCredential` | ~53,600 |
| `revokeCredential` | ~48,700 |
| 100 certificates one by one | 5,467,600 |
| 100 certificates as one Merkle batch | 75,671 (**98.6 % saved**) |
| `verify` / `verifyInBatch` | free (view call) |

## Project layout

```
contracts/
├── contracts/CredentialRegistry.sol        # the smart contract
├── test/CredentialRegistry.ts              # 21 tests (Mocha + ethers)
├── ignition/modules/CredentialRegistry.ts  # deployment module (admin = account 0)
├── ignition/deployments/chain-11155111/    # Sepolia deployment record (committed)
├── scripts/demo-local.ts                   # end-to-end demo on a local node
├── scripts/demo-sepolia.ts                 # end-to-end demo on a live network
├── scripts/export-backend-abi.ts           # ABI + addresses -> backend/src/main/resources/blockchain
├── demo/                                   # public results of live demo runs
└── hardhat.config.ts                       # solc 0.8.34, networks, Etherscan verification
```

## Prerequisites

- Node.js 22+ and npm
- For Sepolia: a funded test wallet (MetaMask), an RPC URL (Alchemy) and an Etherscan API key

```powershell
cd contracts
npm install
```

## Build and test

```powershell
npx hardhat build                              # development build (no optimizer)
npx hardhat build --build-profile production   # same settings as the deployed contract
npx hardhat test                               # 21 tests
```

## Run locally

```powershell
# terminal 1 – local blockchain (chain 31337)
npx hardhat node

# terminal 2
npx hardhat ignition deploy ignition/modules/CredentialRegistry.ts --network localhost
npx hardhat run scripts/demo-local.ts --network localhost
```

## Deploy to Sepolia

Secrets are kept **only** in Hardhat's encrypted keystore – never in files or Git.

```powershell
npx hardhat keystore set SEPOLIA_RPC_URL       # Alchemy HTTPS endpoint for Ethereum Sepolia
npx hardhat keystore set SEPOLIA_PRIVATE_KEY   # MetaMask key, with 0x prefix
npx hardhat keystore set ETHERSCAN_API_KEY     # etherscan.io API key token
npx hardhat keystore list

npx hardhat ignition deploy ignition/modules/CredentialRegistry.ts --network sepolia
```

### Verify the source code

Ignition deploys with the **production** profile (optimizer on, 200 runs), so build with the same profile first:

```powershell
npx hardhat build --build-profile production
npx hardhat verify --network sepolia <CONTRACT_ADDRESS> <ADMIN_ADDRESS>
```

### Live demo

```powershell
npx hardhat run scripts/demo-sepolia.ts --network sepolia
```

Approves the wallet as issuer, issues a certificate (VALID), detects a tampered copy (NOT_FOUND),
issues and revokes another (REVOKED), and issues a Merkle batch of 3 (VALID ×3).
Results are saved to `demo/demo-result-chain-<chainId>.json`.

## Export for the backend (web3j)

```powershell
npx hardhat run scripts/export-backend-abi.ts
```

Writes `CredentialRegistry.abi`, `CredentialRegistry.bin` and `credential-registry-deployments.json`
to `backend/src/main/resources/blockchain/`, taken from the Ignition deployment records so they always
match what is on-chain. Run it again after every new deployment.

## Security notes

- Recovery phrase: paper only. Private key, RPC URL and Etherscan key: keystore only.
- The Sepolia wallet is for testing; a production launch would use a hardware wallet or multisig as admin.
- Contract address, ABI, transaction hashes and demo results are public data.