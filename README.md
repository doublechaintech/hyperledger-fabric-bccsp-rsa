# 驱动

经过多轮测试，双链科技在其股权管理区块链实验网中成功支持RSA证书。让Fabric支持RSA证书，
使得双链科技可以正式为国际国内金融机构和非金融机构提供完整的跨组织、跨境商用联盟链解决方案。

Fabric自发布到目前最新的1.3版本，一直只是支持ECDSA-secp256k1【1】，但是国内CA机构因为合规的原因无法提供该算法
的证书，这个情况导致使用原生Fabric方案的国内技术提供商无法在法律上解决数字世界中独立法人机构和现实对等的义务
和权利问题，亦无法解决金融机构之间数据交换的合规性问题。

# 改造
双链科技意识到这个是Fabric落地的主要障碍，决心改造Fabric，使联盟链在国内真正落地，让在区块链网络中多方权益受
电子签名法保护。

通过仔细分析超级账本Fabric 成员服务（MSP，Member Service Provider）源码，双链科技的工程师确定并且实施了
改造方案，最终在近日首次测试成功，后又经过多轮验证，确认RSA正确集成到了超级账本Fabric中。

# 影响

根据公开资料以及向超级账本中国社区求证，Fabric支持RSA证书尚属首次。

双链科技本着使用开源，回馈开源社区的基本原则，经过代码重构、内部审核之后，今天公布其所有相关源代码并请求合并
到Fabric主干中。

#双链科技背景
双链科技是一家以区块链技术改造集成供应链为使命的创新型科技公司，将以创新型技术和业务解决方案整合境内和跨境供应链资金流，物流，商流，信息流和人才流，并且利用区块链技术跨越企业边界，建立新型安全可信系统和新型价值连接。



# Hyperledger Fabric介绍
Hyperledger Fabric是一个许可的区块链构架(permissioned blockchain infrastructure)。其由IBM[8] 和Digital Asset最初贡献给Hyperledger项目。超级账本项目创立成员包括：荷兰银行、埃森哲、Calastone、思科、富士通、Guardtime、日立、IBM、英特尔、IntellectEU、JP摩根、NEC、NTT DATA等。除此之外，超级账本大中华区会员单位还包括阿里云，百度、万达集团、腾讯、华为、小米等。

# 名词解释和技术术语
￼
RSA加密算法是一种非对称加密算法。在公开密钥加密和电子商业中RSA被广泛使用。

secp256k1
椭圆曲线算法简单的说就是用X和Y坐标画一个曲线。这个曲线怎么画，需要很多个参数来确定。以太坊使用了一套叫secp256k1的参数确定了椭圆的形状。所以，以太坊的签名算法全称就是是ECDSA-secp256k1。 目前尚无持牌照的机构颁发此证书。


````
参考资料

【1】Fabric1.1 不支持RSA证书 http://hyperledger-fabric.readthedocs.io/en/release-1.3/msp.html， 截图
【2】Fabric1.1 只支持ECDSA-secp256k1证书http://hyperledger-fabric.readthedocs.io/en/release-1.3/msp.html
【3】来自维基百科
【4】来自维基百科

A RSA toolchain to allow Hyperledger Fabric support RSA certificates
````
