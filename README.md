# RecoverEth
Recovers eth sent to a contract on the wrong chain

## Background

I was playing with a prototype and sent 1.2 Eth to an address on Mainnet instead of 1.2 Polygon to a contract on Polygon.

Luckily, there was no activity on the corresponding mainnet address, so it was possible to recover the funds
Unluckily, the contract was written on nonce 52, so it would require 51 empty transactions on the main net chain before the recovery contract can be written to the corresponding address.


# To Use:

1. Use Etherscan (or equivalent) to work out the nonce of the contract deployment on the chain you wanted to send the funds to, eg 52. 
2. Set the ```TARGET_NONCE``` value in ```class RecoverEth``` to this value.
3. Copy/Paste the private key that deployed the contract into the ```PRIVATE_KEY``` value eg ```PRIVATE_KEY = "123456789abcdef123456789abcdef123456789abcdef123456789abcdef1234";```
4. Set the DEPLOYMENT_CHAIN to the chain that you accidentally sent the funds to, eg ```DEPLOYMENT_CHAIN = MAINNET_ID```. You can also just use the chain number directly
5. Open the recovereth.sol contract and change the destAddr payment address to your address.
6. Compile using solcjs (or whichever Solidity compiler you like) ```>solcjs recovereth.sol --base-path . --optimize --bin```
7. Copy the hex in the generated .bin file to the PAYOUT_CONTRACT eg ```PAYOUT_CONTRACT = "0x608060...";```
8. Ensure you have a node setup for the chain you're using in ```private Web3j getWeb3j() {```
9. Double check all the settings! You only have one go at this. You may want to do a test-run on a testnet using the same values.
10. Use 

```./gradlew run```

Watch the transactions appear until you see the final "Send Ctor transaction" text. The program will wait for you to manually terminate it.

Finally check Etherscan to make sure everything worked. Your funds should be back in your account.
