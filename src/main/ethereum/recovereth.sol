pragma solidity ^0.8.4;

contract RecoverEth  {
    address payable destAddr = payable(0xC067A53c91258ba513059919E03B81CF93f57Ac7);
    uint256 amt = 1200000000000000000;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------
    constructor() {
        destAddr.call{value: amt}("");
    }

    function payVal(uint256 val) public payable {
        destAddr.call{value: val}("");
    }

    function endContract() public {
        if (msg.sender == destAddr) {
            selfdestruct(destAddr);
        }
    }
}