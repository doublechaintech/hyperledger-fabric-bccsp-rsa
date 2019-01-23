# hyperledger-fabric-bccsp-rsa
A RSA toolchain to allow Hyperledger Fabric support RSA certificates
This docment introduce how to use the rsa certs in fabric.
The OOTB fabric uses ecc , and we will use the plugin factory and  create the rsa plugin  to support the rsa certs.

## Prerequisites
* install go   version 1.11.x is required.
https://golang.org/dl/

* install make
we will use make to compile the  peer/order etc bin.

* install git
we will use git to download the  fabric code base.


## Steps

### Step 1: Download the codebase from git
```
mkdir -p $GOPATH/src/github.com/hyperledger
cd  $GOPATH/src/github.com/hyperledger
git clone https://github.com/hyperledger/fabric.git
and please checkout the correct branch
```

### Step 2: build the bins

```
cd  $GOPATH/src/github.com/hyperledger/fabric
make  peer
make  orderer

Note: 
a. we cannot use the docker images, since the docker images cannot link the plugins.
We will create our own bins and  run that bin on the peer or orderer directly. Also we will
build our rsa plugin based on the fabric codebase.
b.  please  export the right go envs , then you can run that bin on the right machines.
```


### Step 3:  build the plugin

```
also checkout the rsa plugin here:
https://github.com/doublechaintech/hyperledger-fabric-bccsp-rsa/tree/master/bccsp/src

use go build -buildmode=plugin -o rsa.so 
```

### Step 4: config the new  core.yaml  for peer, and  orderer.yaml for orderer
```
example:(TODO)
https://github.com/philipgreat/hyperledger-fabric/blob/master/config/core.yaml,please take the PLUGIN section, you need add that.
https://github.com/philipgreat/hyperledger-fabric/blob/master/config/orderer.yaml,please take the PLUGIN section, you need add that.
```

### Step 5: start Orderer and Peer
```
example:(TODO)
https://github.com/philipgreat/hyperledger-fabric/blob/master/scripts/startPeer-bin-withplugin.sh
https://github.com/philipgreat/hyperledger-fabric/blob/master/scripts/startOrderer-bin-withplugin.sh
```


