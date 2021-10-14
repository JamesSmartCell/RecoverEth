package org.stormbird.recovereth;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class RecoverEth
{
    public static final int MAINNET_ID = 1;
    public static final int CLASSIC_ID = 61;
    public static final int POA_ID = 99;
    public static final int KOVAN_ID = 42;
    public static final int ROPSTEN_ID = 3;
    public static final int SOKOL_ID = 77;
    public static final int RINKEBY_ID = 4;
    public static final int XDAI_ID = 100;
    public static final int GOERLI_ID = 5;
    public static final int ARTIS_SIGMA1_ID = 246529;
    public static final int ARTIS_TAU1_ID = 246785;
    public static final int BINANCE_ID = 56;
    public static final int HECO_ID = 128;
    public static final int MATIC_ID = 137;

    private static final String PRIVATE_KEY = "";  //<-- insert private key that created the original contract on the different chain
    //This is the compiled contract bytes of recovereth.sol. If you encounter the same issue, you'll need to replace the destAddr address in recovereth.sol and compile with >solcjs recovereth.sol --base-path . --optimize --bin
    private static final String PAYOUT_CONTRACT = "0x6080604052600080546001600160a01b03191673c067a53c91258ba513059919e03b81cf93f57ac71790556710a741a46278000060015534801561004257600080fd5b506000546001546040516001600160a01b039092169160006040518083038185875af1925050503d8060008114610095576040519150601f19603f3d011682016040523d82523d6000602084013e61009a565b606091505b505050610113806100ac6000396000f3fe60806040526004361060265760003560e01c80631603a08314602b578063bb6e7de914603c575b600080fd5b603a603636600460c6565b604e565b005b348015604757600080fd5b50603a60a4565b600080546040516001600160a01b039091169183919081818185875af1925050503d80600081146099576040519150601f19603f3d011682016040523d82523d6000602084013e609e565b606091505b50505050565b6000546001600160a01b031633141560c4576000546001600160a01b0316ff5b565b60006020828403121560d6578081fd5b503591905056fea2646970667358221220fdd3c4f2ec5296d9e20cf86ec40c3b823038c76dd332602c8770645f4d6acea164736f6c63430008040033";
    private static final int TARGET_NONCE = 70; //match with the nonce that created the contract you accidentally sent to on the wrong chain
    private static final int DEPLOYMENT_CHAIN = MAINNET_ID;
    private static final BigDecimal GWEI_THRESHOLD = BigDecimal.valueOf(77); // only push transactions when gas is at this Gwei or below

    private static final BigInteger targetNonce = BigInteger.valueOf(TARGET_NONCE);
    private static final BigDecimal GWEI_FACTOR = BigDecimal.valueOf(1000000000L);

    private Web3j web3j;
    private Credentials credentials;
    private OkHttpClient client;

    Disposable gasChecker;

    public static void main(String[] args)
    {
        RecoverEth runner = new RecoverEth();
        runner.go();

        while(true); //hold so the log can be read
    }

    public void go()
    {
        client = buildClient();
        web3j = getWeb3j(DEPLOYMENT_CHAIN);
        credentials = Credentials.create(PRIVATE_KEY);

        startGasChecker();
    }

    private void startGasChecker()
    {
        gasChecker = Observable.interval(0, 30, TimeUnit.SECONDS)
                .doOnNext(l -> checkGas()).subscribe();
    }

    private void checkGas()
    {
        BigDecimal baseGasPrice = new BigDecimal(getGasPrice());
        BigDecimal gasPriceGwei = baseGasPrice.divide(GWEI_FACTOR);

        System.out.println("Gas Price: " + gasPriceGwei);

        if (gasPriceGwei.compareTo(GWEI_THRESHOLD) <= 0)
        {
            System.out.println("Start Transaction send, Gas: " + gasPriceGwei.toString());
            //stop the regular updates
            if (gasChecker != null && !gasChecker.isDisposed()) gasChecker.dispose();

            BigInteger raisedGasPrice = baseGasPrice.multiply(BigDecimal.valueOf(1.3)).toBigInteger(); //use 1.3x base price for constructor tx
            BigInteger bargainGasPrice = baseGasPrice.multiply(BigDecimal.valueOf(0.7)).toBigInteger();

            //kick off 10 or less writes to reach the nonce //credentials.getAddress()
            long currentNonce = getLastTransactionNonce(web3j, credentials.getAddress()).blockingGet().longValue();
            String keyAddress = credentials.getAddress();
            int index = 0;
            String txHash = "";

            for (; currentNonce < targetNonce.longValue(); currentNonce++)
            {
                BigInteger gasPrice = (index == 9) ? raisedGasPrice : bargainGasPrice; //use good gas price for the final tx in the batch
                txHash = createTransactionWithNonce(keyAddress, currentNonce, gasPrice, BigInteger.valueOf(23000L), Numeric.hexStringToByteArray("0x"), DEPLOYMENT_CHAIN).blockingGet();
                System.out.println("Sending tx nonce: " + currentNonce + " : " + txHash);
                if (++index >= 10) break;
            }

            if (currentNonce == targetNonce.longValue())
            {
                //Now write the payout contract at the target nonce to match the contract deployed on the other network
                String txReceipt = createConstructorUsingNonce(targetNonce, raisedGasPrice, BigInteger.valueOf(170000L), PAYOUT_CONTRACT, "", DEPLOYMENT_CHAIN).blockingGet();
                System.out.println("Send CTor transaction: " + txReceipt);
            }
            else if (txHash.length() > 0)
            {
                waitForTransactionReceipt(txHash);
            }
            else
            {
                startGasChecker();
            }
        }
    }

    private BigInteger getGasPrice()
    {
        BigInteger gasPrice = BigInteger.valueOf(2000000000L);
        try {
            gasPrice = web3j.ethGasPrice().send().getGasPrice();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return gasPrice;
    }

    public Single<String> createConstructorUsingNonce(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String contractData, String constructorData, int chainId)
    {
        String fullDeployment = contractData + constructorData;

        return getRawTransaction(nonce, gasPrice, gasLimit, BigInteger.ZERO, fullDeployment)
                .flatMap(rawTx -> signEncodeRawTransaction(rawTx, chainId))
                .flatMap(signedMessage -> Single.fromCallable( () -> {
                    EthSendTransaction raw = web3j
                            .ethSendRawTransaction(Numeric.toHexString(signedMessage))
                            .send();
                    if (raw.hasError()) {
                        throw new Exception(raw.getError().getMessage());
                    }
                    return raw.getTransactionHash();
                }));
    }

    private Single<RawTransaction> getRawTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, BigInteger value, String data)
    {
        return Single.fromCallable(() ->
                RawTransaction.createContractTransaction(
                        nonce,
                        gasPrice,
                        gasLimit,
                        value,
                        data));
    }

    private Single<byte[]> signEncodeRawTransaction(RawTransaction rtx, int chainId)
    {
        return Single.fromCallable(() -> TransactionEncoder.signMessage(rtx, chainId, credentials));
    }

    public Single<String> createTransactionWithNonce(String toAddress, long nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] data, int chainId)
    {
        return signTransaction(toAddress, BigInteger.ZERO, gasPrice, gasLimit, nonce, data, chainId)
                .flatMap(signedMessage -> Single.fromCallable(() -> {
                    EthSendTransaction raw = web3j
                            .ethSendRawTransaction(Numeric.toHexString(signedMessage))
                            .send();
                    if (raw.hasError())
                    {
                        throw new Exception(raw.getError().getMessage());
                    }
                    return raw.getTransactionHash();
                }));
    }

    public Single<BigInteger> getLastTransactionNonce(Web3j web3j, String walletAddress)
    {
        return Single.fromCallable(() -> {
            try
            {
                EthGetTransactionCount ethGetTransactionCount = web3j
                        .ethGetTransactionCount(walletAddress, DefaultBlockParameterName.PENDING)
                        .send();
                return ethGetTransactionCount.getTransactionCount();
            }
            catch (Exception e)
            {
                return BigInteger.ZERO;
            }
        });
    }

    private Web3j getWeb3j(int chainId) {
        //Infura
        HttpService nodeService;
        if (chainId == MAINNET_ID)
        {
            nodeService = new HttpService("https://mainnet.infura.io/v3/da3717f25f824cc1baa32d812386d93f", client, false);
        }
        else if (chainId == RINKEBY_ID)
        {
            nodeService = new HttpService("https://rinkeby.infura.io/v3/da3717f25f824cc1baa32d812386d93f", client, false);
        }
        else if (chainId == ROPSTEN_ID)
        {
            nodeService = new HttpService("https://ropsten.infura.io/v3/da3717f25f824cc1baa32d812386d93f", client, false);
        }
        else
        {
            nodeService = null;
        }

        return Web3j.build(nodeService);
    }

    public Single<byte[]> signTransaction(String toAddress, BigInteger amount, BigInteger gasPrice, BigInteger gasLimit, long nonce, byte[] data, long chainId) {
        return Single.fromCallable(() -> {
            String dataStr = data != null ? Numeric.toHexString(data) : "";

            RawTransaction rtx = RawTransaction.createTransaction(
                    BigInteger.valueOf(nonce),
                    gasPrice,
                    gasLimit,
                    toAddress,
                    amount,
                    dataStr
            );

            return TransactionEncoder.signMessage(rtx, chainId, credentials);
        });
    }

    private void waitForTransactionReceipt(final String resultHash)
    {
        Single.fromCallable(() -> {
                    sleep(1000);
                    EthTransaction etx = web3j.ethGetTransactionByHash(resultHash).send();
                    return (etx == null) ? new EthTransaction() : etx;
        }).subscribeOn(Schedulers.io())
                .subscribe(etx -> checkEthTransaction(etx, resultHash)).isDisposed();
    }

    private void checkEthTransaction(@NonNull EthTransaction etx, final String resultHash)
    {
        if (etx.getResult() != null && etx.getResult().getBlockNumberRaw() != null && !etx.getResult().getBlockNumberRaw().equals("null"))
        {
            System.out.println("Tx written: " + etx.getResult().getHash());
            //restart gas price listener
            System.out.println("Restarting Gas Listener");
            startGasChecker();
        }
        else
        {
            waitForTransactionReceipt(resultHash);
        }
    }

    private OkHttpClient buildClient()
    {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }
}
