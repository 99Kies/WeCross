package com.webank.wecross.routine.htlc;

import com.webank.wecross.exception.WeCrossException;
import com.webank.wecross.resource.Resource;
import com.webank.wecross.routine.RoutineDefault;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeaderManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.VerifiedTransaction;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeCrossHTLC implements HTLC {
    private Logger logger = LoggerFactory.getLogger(WeCrossHTLC.class);

    public String call(HTLCResource htlcResource, String method, String... args)
            throws WeCrossException {
        TransactionRequest request = new TransactionRequest(method, args);

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<TransactionRequest>(
                        request,
                        htlcResource.getAccount(),
                        htlcResource.getResourceInfo(),
                        htlcResource.getResourceBlockHeaderManager());
        if (!method.equals("getTask")) {
            logger.info(
                    "call request: {}, path: {}",
                    request.toString(),
                    htlcResource.getSelfPath().toString());
        }
        TransactionResponse response = htlcResource.call(transactionContext);
        if (response.getErrorCode() != 0) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_TRANSACTION_ERROR,
                    method + " failed",
                    response.getErrorCode(),
                    response.getErrorMessage());
        }
        if (!method.equals("getTask")) {
            logger.info("call response: {}", response.toString());
        }
        return response.getResult()[0].trim();
    }

    public String sendTransaction(HTLCResource htlcResource, String method, String... args)
            throws WeCrossException {
        TransactionRequest request = new TransactionRequest(method, args);

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<TransactionRequest>(
                        request,
                        htlcResource.getAccount(),
                        htlcResource.getResourceInfo(),
                        htlcResource.getResourceBlockHeaderManager());
        logger.info(
                "sendTransaction request: {}, path: {}",
                request.toString(),
                htlcResource.getSelfPath().toString());
        TransactionResponse response = htlcResource.sendTransaction(transactionContext);
        if (response.getErrorCode() != 0) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_TRANSACTION_ERROR,
                    method + " failed",
                    response.getErrorCode(),
                    response.getErrorMessage());
        }
        logger.info("sendTransaction response: {}", response.toString());
        String[] result = response.getResult();
        if (result == null || result.length == 0) {
            return null;
        } else {
            return result[0].trim();
        }
    }

    @Override
    public TransactionResponse lock(HTLCResource htlcResource, String h) throws WeCrossException {
        TransactionRequest request = new TransactionRequest("lock", new String[] {h});

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<TransactionRequest>(
                        request,
                        htlcResource.getAccount(),
                        htlcResource.getResourceInfo(),
                        htlcResource.getResourceBlockHeaderManager());
        logger.info(
                "lock request: {}, path: {}",
                request.toString(),
                htlcResource.getSelfPath().toString());
        TransactionResponse response = htlcResource.sendTransaction(transactionContext);
        if (response.getErrorCode() != 0) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_TRANSACTION_ERROR,
                    "lock failed",
                    response.getErrorCode(),
                    response.getErrorMessage());
        }
        logger.info("lock response: {}", response.toString());
        if (response.getResult()[0].trim().equalsIgnoreCase(RoutineDefault.SUCCESS_FLAG)) {
            String lockTxHash = response.getHash();
            long lockTxBlockNum = response.getBlockNumber();
            setLockTxInfo(htlcResource, h, lockTxHash, lockTxBlockNum);
        }
        return response;
    }

    // lock counterparty
    @Override
    public void lockWithVerify(HTLCResource htlcResource, String address, String h)
            throws WeCrossException {
        TransactionResponse response = lock(htlcResource, h);
        if (!response.getResult()[0].trim().equalsIgnoreCase(RoutineDefault.SUCCESS_FLAG)) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_LOCK_ERROR, response.getResult()[0].trim());
        }
        VerifyData verifyData =
                new VerifyData(
                        response.getBlockNumber(),
                        response.getHash(),
                        address,
                        "lock",
                        new String[] {h},
                        new String[] {RoutineDefault.SUCCESS_FLAG});

        if (!verify(htlcResource, verifyData)) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_VERIFY_ERROR, "verify transaction of lock failed");
        }
    }

    @Override
    public TransactionResponse unlock(
            HTLCResource htlcResource, String txHash, long blockNumber, String h, String s)
            throws WeCrossException {
        TransactionRequest request =
                new TransactionRequest(
                        "unlock", new String[] {h, s, txHash, String.valueOf(blockNumber)});

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<TransactionRequest>(
                        request,
                        htlcResource.getAccount(),
                        htlcResource.getResourceInfo(),
                        htlcResource.getResourceBlockHeaderManager());
        logger.info(
                "unlock request: {}, path: {}",
                request.toString(),
                htlcResource.getSelfPath().toString());
        TransactionResponse response = htlcResource.sendTransaction(transactionContext);
        if (response.getErrorCode() != 0) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_TRANSACTION_ERROR,
                    "unlock failed",
                    response.getErrorCode(),
                    response.getErrorMessage());
        }
        logger.info("unlock response: {}", response.toString());
        return response;
    }

    // unlock counterparty
    @Override
    public void unlockWithVerify(
            HTLCResource htlcResource,
            String txHash,
            long blockNumber,
            String address,
            String h,
            String s)
            throws WeCrossException {
        TransactionResponse response = unlock(htlcResource, txHash, blockNumber, h, s);
        if (!response.getResult()[0].trim().equalsIgnoreCase(RoutineDefault.SUCCESS_FLAG)) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_UNLOCK_ERROR, response.getResult()[0].trim());
        }
        VerifyData verifyData =
                new VerifyData(
                        response.getBlockNumber(),
                        response.getHash(),
                        address,
                        "unlock",
                        new String[] {h, s, txHash, String.valueOf(blockNumber)},
                        new String[] {RoutineDefault.SUCCESS_FLAG});

        if (!verify(htlcResource, verifyData)) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_VERIFY_ERROR, "verify transaction of lock failed");
        }
    }

    @Override
    public String rollback(HTLCResource htlcResource, String h) throws WeCrossException {
        return sendTransaction(htlcResource, "rollback", h);
    }

    @Override
    public boolean verify(Resource resource, VerifyData verifyData) {
        String txHash = verifyData.getTransactionHash();
        long blockNumber = verifyData.getBlockNumber();
        BlockHeaderManager blockHeaderManager = resource.getResourceBlockHeaderManager();
        Connection connection = resource.chooseConnection();
        Driver driver = resource.getDriver();

        VerifiedTransaction verifiedTransaction =
                driver.getVerifiedTransaction(txHash, blockNumber, blockHeaderManager, connection);

        return verifyData.equals(verifiedTransaction);
    }

    @Override
    public String getCounterpartyHtlc(Resource resource, Account account) throws WeCrossException {
        TransactionRequest transactionRequest =
                new TransactionRequest("getCounterpartyHtlc", new String[] {});

        TransactionResponse response =
                (TransactionResponse)
                        resource.call(
                                new TransactionContext<TransactionRequest>(
                                        transactionRequest,
                                        account,
                                        resource.getResourceInfo(),
                                        resource.getResourceBlockHeaderManager()));

        String[] result = response.getResult();
        if (response.getErrorCode() != 0
                || result[0].trim().equalsIgnoreCase(RoutineDefault.NULL_FLAG)) {
            throw new WeCrossException(
                    HTLCErrorCode.ASSET_HTLC_TRANSACTION_ERROR,
                    "getCounterpartyHtlc failed",
                    response.getErrorCode(),
                    response.getErrorMessage());
        }
        return result[0].trim();
    }

    @Override
    public String getTask(HTLCResource htlcResource) throws WeCrossException {
        return call(htlcResource, "getTask");
    }

    @Override
    public String deleteTask(HTLCResource htlcResource, String h) throws WeCrossException {
        return sendTransaction(htlcResource, "deleteTask", h);
    }

    @Override
    public String getSecret(HTLCResource htlcResource, String h) throws WeCrossException {
        return call(htlcResource, "getSecret", h);
    }

    @Override
    public String[] getNewContractTxInfo(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String info = call(htlcResource, "getNewContractTxInfo", h);
        if (info.equalsIgnoreCase(RoutineDefault.NULL_FLAG)) {
            String errorMsg =
                    "newContractTxInfo not found, h: "
                            + h
                            + " path: "
                            + htlcResource.getSelfPath().toString();
            throw new WeCrossException(HTLCErrorCode.ASSET_HTLC_GET_TX_INFO_ERROR, errorMsg);
        }
        return info.split(" ");
    }

    @Override
    public String[] getLockTxInfo(HTLCResource htlcResource, String h) throws WeCrossException {
        String info = call(htlcResource, "getLockTxInfo", h);
        if (info.equalsIgnoreCase(RoutineDefault.NULL_FLAG)) {
            String errorMsg =
                    "lockTxInfo not found, h: "
                            + h
                            + " path: "
                            + htlcResource.getSelfPath().toString();
            throw new WeCrossException(HTLCErrorCode.ASSET_HTLC_GET_TX_INFO_ERROR, errorMsg);
        }
        return info.split(" ");
    }

    @Override
    public void setLockTxInfo(HTLCResource htlcResource, String h, String txHash, long blockNumber)
            throws WeCrossException {
        sendTransaction(htlcResource, "setLockTxInfo", h, txHash, String.valueOf(blockNumber));
    }

    @Override
    public BigInteger getSelfTimelock(HTLCResource htlcResource, String h) throws WeCrossException {
        return new BigInteger(call(htlcResource, "getSelfTimelock", h));
    }

    @Override
    public BigInteger getCounterpartyTimelock(HTLCResource htlcResource, String h)
            throws WeCrossException {
        return new BigInteger(call(htlcResource, "getCounterpartyTimelock", h));
    }

    @Override
    public boolean getSelfLockStatus(HTLCResource htlcResource, String h) throws WeCrossException {
        String result = call(htlcResource, "getSelfLockStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public boolean getCounterpartyLockStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String result = call(htlcResource, "getCounterpartyLockStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public boolean getSelfUnlockStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String result = call(htlcResource, "getSelfUnlockStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public boolean getCounterpartyUnlockStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String result = call(htlcResource, "getCounterpartyUnlockStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public boolean getSelfRollbackStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String result = call(htlcResource, "getSelfRollbackStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public boolean getCounterpartyRollbackStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        String result = call(htlcResource, "getCounterpartyRollbackStatus", h);
        return result.equalsIgnoreCase(RoutineDefault.TRUE_FLAG);
    }

    @Override
    public void setCounterpartyLockStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        sendTransaction(htlcResource, "setCounterpartyLockStatus", h);
    }

    @Override
    public void setCounterpartyUnlockStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        sendTransaction(htlcResource, "setCounterpartyUnlockStatus", h);
    }

    @Override
    public void setCounterpartyRollbackStatus(HTLCResource htlcResource, String h)
            throws WeCrossException {
        sendTransaction(htlcResource, "setCounterpartyRollbackStatus", h);
    }
}