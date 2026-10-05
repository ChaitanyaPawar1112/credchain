// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {MerkleProof} from "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

/**
 * @title  CredChain Credential Registry
 * @notice Tamper-proof registry of academic credential fingerprints.
 *         Only SHA-256 hashes of certificates are stored, never personal data.
 *         Approved institutions (ISSUER_ROLE) can issue one certificate at a time
 *         or a whole batch with a single Merkle root. Anyone can verify for free.
 */
contract CredentialRegistry is AccessControl, Pausable {
    string public constant VERSION = "1.0.0";

    /// @notice Granted to wallets of institutions approved on the CredChain platform.
    bytes32 public constant ISSUER_ROLE = keccak256("ISSUER_ROLE");

    enum Status {
        NOT_FOUND,
        VALID,
        REVOKED,
        EXPIRED
    }

    enum RevocationReason {
        UNSPECIFIED,
        ISSUED_IN_ERROR,
        FRAUD,
        SUPERSEDED,
        OTHER
    }

    /// @dev Field order packs each record into exactly 2 storage slots.
    struct Credential {
        address issuer;     // slot 1 (20 bytes)
        uint64 issuedAt;    // slot 1 (8 bytes)
        uint64 expiresAt;   // slot 2 - 0 means it never expires
        uint64 revokedAt;   // slot 2 - 0 means not revoked
    }

    struct Batch {
        address issuer;     // slot 1
        uint64 issuedAt;    // slot 1
        uint64 expiresAt;   // slot 2
        uint64 revokedAt;   // slot 2
        uint32 count;       // slot 2 - number of certificates in the batch
    }

    mapping(bytes32 certHash => Credential) private _credentials;
    mapping(bytes32 merkleRoot => Batch) private _batches;
    mapping(bytes32 entryKey => uint64 revokedAt) private _revokedBatchEntries;

    // ---------------------------------------------------------------- events

    event CredentialIssued(bytes32 indexed certHash, address indexed issuer, uint64 expiresAt);
    event BatchIssued(bytes32 indexed merkleRoot, address indexed issuer, uint32 count, uint64 expiresAt);
    event CredentialRevoked(bytes32 indexed certHash, address indexed revokedBy, RevocationReason reason);
    event BatchRevoked(bytes32 indexed merkleRoot, address indexed revokedBy, RevocationReason reason);
    event BatchEntryRevoked(
        bytes32 indexed merkleRoot, bytes32 indexed certHash, address indexed revokedBy, RevocationReason reason
    );

    // ---------------------------------------------------------------- errors

    error ZeroAddress();
    error ZeroHash();
    error EmptyBatch();
    error InvalidExpiry(uint64 expiresAt);
    error AlreadyIssued(bytes32 hash);
    error NotFound(bytes32 hash);
    error AlreadyRevoked(bytes32 hash);
    error NotAuthorizedToRevoke(address caller);
    error InvalidProof();

    // ---------------------------------------------------------------- setup

    /// @param admin Platform wallet that manages issuers (the CredChain backend).
    constructor(address admin) {
        if (admin == address(0)) revert ZeroAddress();
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    // ---------------------------------------------------------------- issuer management (admin)

    function addIssuer(address issuer) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (issuer == address(0)) revert ZeroAddress();
        _grantRole(ISSUER_ROLE, issuer);
    }

    function removeIssuer(address issuer) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _revokeRole(ISSUER_ROLE, issuer);
    }

    function isIssuer(address account) external view returns (bool) {
        return hasRole(ISSUER_ROLE, account);
    }

    /// @notice Emergency stop for issuing. Revocation still works while paused.
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    // ---------------------------------------------------------------- issuing (institutions)

    /// @param certHash  SHA-256 of the certificate document.
    /// @param expiresAt Unix time when it stops being valid, or 0 for never.
    function issueCredential(bytes32 certHash, uint64 expiresAt) external onlyRole(ISSUER_ROLE) whenNotPaused {
        if (certHash == bytes32(0)) revert ZeroHash();
        _checkExpiry(expiresAt);
        if (_credentials[certHash].issuer != address(0)) revert AlreadyIssued(certHash);

        _credentials[certHash] = Credential({
            issuer: msg.sender,
            issuedAt: uint64(block.timestamp),
            expiresAt: expiresAt,
            revokedAt: 0
        });
        emit CredentialIssued(certHash, msg.sender, expiresAt);
    }

    /// @notice Anchors a whole batch (e.g. one convocation) in a single transaction.
    /// @param merkleRoot Root of a Merkle tree whose leaves are leafOf(certHash).
    function issueBatch(bytes32 merkleRoot, uint32 count, uint64 expiresAt)
        external
        onlyRole(ISSUER_ROLE)
        whenNotPaused
    {
        if (merkleRoot == bytes32(0)) revert ZeroHash();
        if (count == 0) revert EmptyBatch();
        _checkExpiry(expiresAt);
        if (_batches[merkleRoot].issuer != address(0)) revert AlreadyIssued(merkleRoot);

        _batches[merkleRoot] = Batch({
            issuer: msg.sender,
            issuedAt: uint64(block.timestamp),
            expiresAt: expiresAt,
            revokedAt: 0,
            count: count
        });
        emit BatchIssued(merkleRoot, msg.sender, count, expiresAt);
    }

    // ---------------------------------------------------------------- revoking (issuer or admin)

    function revokeCredential(bytes32 certHash, RevocationReason reason) external {
        Credential storage c = _credentials[certHash];
        if (c.issuer == address(0)) revert NotFound(certHash);
        _checkCanRevoke(c.issuer);
        if (c.revokedAt != 0) revert AlreadyRevoked(certHash);

        c.revokedAt = uint64(block.timestamp);
        emit CredentialRevoked(certHash, msg.sender, reason);
    }

    /// @notice Revokes every certificate in a batch.
    function revokeBatch(bytes32 merkleRoot, RevocationReason reason) external {
        Batch storage b = _batches[merkleRoot];
        if (b.issuer == address(0)) revert NotFound(merkleRoot);
        _checkCanRevoke(b.issuer);
        if (b.revokedAt != 0) revert AlreadyRevoked(merkleRoot);

        b.revokedAt = uint64(block.timestamp);
        emit BatchRevoked(merkleRoot, msg.sender, reason);
    }

    /// @notice Revokes ONE certificate inside a batch; the rest of the batch stays valid.
    function revokeBatchEntry(
        bytes32 merkleRoot,
        bytes32 certHash,
        bytes32[] calldata proof,
        RevocationReason reason
    ) external {
        Batch storage b = _batches[merkleRoot];
        if (b.issuer == address(0)) revert NotFound(merkleRoot);
        _checkCanRevoke(b.issuer);
        if (!MerkleProof.verifyCalldata(proof, merkleRoot, leafOf(certHash))) revert InvalidProof();

        bytes32 key = _entryKey(merkleRoot, certHash);
        if (_revokedBatchEntries[key] != 0) revert AlreadyRevoked(certHash);

        _revokedBatchEntries[key] = uint64(block.timestamp);
        emit BatchEntryRevoked(merkleRoot, certHash, msg.sender, reason);
    }

    // ---------------------------------------------------------------- verifying (anyone, free)

    function verify(bytes32 certHash)
        external
        view
        returns (Status status, address issuer, uint64 issuedAt, uint64 expiresAt)
    {
        Credential memory c = _credentials[certHash];
        if (c.issuer == address(0)) {
            return (Status.NOT_FOUND, address(0), 0, 0);
        }
        return (_status(c.revokedAt, c.expiresAt), c.issuer, c.issuedAt, c.expiresAt);
    }

    function verifyInBatch(bytes32 merkleRoot, bytes32 certHash, bytes32[] calldata proof)
        external
        view
        returns (Status status, address issuer, uint64 issuedAt, uint64 expiresAt)
    {
        Batch memory b = _batches[merkleRoot];
        if (b.issuer == address(0) || !MerkleProof.verifyCalldata(proof, merkleRoot, leafOf(certHash))) {
            return (Status.NOT_FOUND, address(0), 0, 0);
        }
        uint64 revokedAt = b.revokedAt != 0 ? b.revokedAt : _revokedBatchEntries[_entryKey(merkleRoot, certHash)];
        return (_status(revokedAt, b.expiresAt), b.issuer, b.issuedAt, b.expiresAt);
    }

    function getCredential(bytes32 certHash) external view returns (Credential memory) {
        return _credentials[certHash];
    }

    function getBatch(bytes32 merkleRoot) external view returns (Batch memory) {
        return _batches[merkleRoot];
    }

    /**
     * @notice Merkle leaf for a certificate hash. Double hashing is OpenZeppelin's recommended
     *         protection against second-preimage attacks, and matches StandardMerkleTree(["bytes32"]).
     */
    function leafOf(bytes32 certHash) public pure returns (bytes32) {
        return keccak256(bytes.concat(keccak256(abi.encode(certHash))));
    }

    // ---------------------------------------------------------------- internal helpers

    /// @dev Revoked takes priority over expired.
    function _status(uint64 revokedAt, uint64 expiresAt) private view returns (Status) {
        if (revokedAt != 0) return Status.REVOKED;
        if (expiresAt != 0 && block.timestamp >= expiresAt) return Status.EXPIRED;
        return Status.VALID;
    }

    function _checkExpiry(uint64 expiresAt) private view {
        if (expiresAt != 0 && expiresAt <= block.timestamp) revert InvalidExpiry(expiresAt);
    }

    function _checkCanRevoke(address issuer) private view {
        if (msg.sender != issuer && !hasRole(DEFAULT_ADMIN_ROLE, msg.sender)) {
            revert NotAuthorizedToRevoke(msg.sender);
        }
    }

    function _entryKey(bytes32 merkleRoot, bytes32 certHash) private pure returns (bytes32) {
        return keccak256(abi.encode(merkleRoot, certHash));
    }
}