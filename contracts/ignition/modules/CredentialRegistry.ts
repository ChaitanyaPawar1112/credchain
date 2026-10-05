import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

/**
 * Deploys CredentialRegistry.
 * The deploying account (account 0) becomes the platform admin (DEFAULT_ADMIN_ROLE).
 * On Sepolia that is the CredChain backend's wallet.
 */
export default buildModule("CredentialRegistryModule", (m) => {
    const admin = m.getAccount(0);
    const registry = m.contract("CredentialRegistry", [admin]);
    return { registry };
});