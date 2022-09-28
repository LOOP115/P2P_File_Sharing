# Peer-To-Peer File Sharing System



### [Specification](idxsrv.md)

### [Questions](resources/Questions-Group30.pdf)



## Commands

```
# Server
mvn clean compile assembly:single
java -cp target/idxsrv-0.0.1-SNAPSHOT-jar-with-dependencies.jar comp90015.idxsrv.IdxSrv

# Peer
mvn clean compile assembly:single
java -cp target/idxsrv-0.0.1-SNAPSHOT-jar-with-dependencies.jar comp90015.idxsrv.Filesharer
```

Note: Use javaw on windows



## VM Server

* Host IP: ```ubuntu@172.26.130.95```
* Send files: `pscp -i "DS\key\id_rsa.ppk" -r idxsrv ubuntu@172.26.130.95:/home/ubuntu`



## Questions

#### 1. (300 words) Explain the strategy that you used in your implementation to download blocks for files, in terms of how you selected the peers to download from (in the case that there was more than one such peer to download from) and how you selected which blocks to download first. What are the reasons for and reasons against downloading the blocks for a file in natural order, i.e. block 0, 1, 2, etc., as opposed to downloading blocks in an arbitrary order.



#### 2. (400 words) Propose and discuss changes to the protocol, either between the peer and the server and/or between peers, that would the system to be more efficient in general -- i.e. to have less overheads -- than the current protocol. You may propose adding more fields to the existing messages or adding more messages in order to do so. You do not need to actually implement these things in your project. Discuss whether your proposed changes are *backwards compatible* or not.



#### 3. (300 words) Explain the purpose of the socket timeout parameter in Project 1, as used by both the client and the server. What kind of failures does it address? Explain what is the problem with making this value very small or very large? How should we select a good value for the socket timeout? List different kinds of failures that could *in theory* happen and discuss how the system either does or does not address these failures.



#### 4. (500 words) Consider the server running on the Ubuntu VM provided in the NeCTAR cloud. We would like to estimate how big the file sharing system can grow, i.e. how many peers can a single server support? What do we need to model in order to estimate this? Explain each aspect that we need to model. What aspects of the system could we measure, at small scale, to help predict the scalability of the system using the model? Measure these things (use a separate branch in your repository to keep your measurement code separate from your actual submitted code) and report them here. Discuss what you conclude would be the limit on how big the file sharing system can grow, when using only a single server. State any assumptions that you have made.

