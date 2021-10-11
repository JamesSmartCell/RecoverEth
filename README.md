# RecoverEth
Recovers eth sent to a contract on the wrong chain

## Background

I was playing with a prototype and sent 1.2 Eth to an address on Mainnet instead of 1.2 Polygon to a contract on Polygon.

Luckily, there was no activity on the corresponding mainnet address, so it was possible to recover the funds
Unluckily, the contract was written on nonce 52, so it would require 51 empty transactions on the main net chain before the recovery contract can be written to the corresponding address.
