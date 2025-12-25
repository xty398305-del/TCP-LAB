package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.*;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;    // 待发送的TCP数据报
    private volatile int flag = 0;
    private int currentSeq = 1;     // 当前序列号

    /* 构造函数 */
    public TCP_Sender() {
        super();    // 调用超类构造函数
        super.initTCP_Sender(this);     // 初始化TCP发送端
    }

    @Override
    // 可靠发送（应用层调用）：封装应用层数据，产生TCP数据报
    public void rdt_send(int dataIndex, int[] appData) {

        // 生成TCP数据报（设置序号和数据字段/校验和）
        currentSeq = dataIndex * appData.length + 1;  // 包序号设置为字节流号
        tcpH.setTh_seq(currentSeq);
        tcpS.setData(appData);
        tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);

        // 计算并设置校验和
        short checksum = CheckSum.computeChkSum(tcpPack);
        tcpH.setTh_sum(checksum);
        tcpPack.setTcpH(tcpH);

        // 发送TCP数据报
        udt_send(tcpPack);
        flag = 0;

        // 等待ACK报文（使用忙等待，RDT2.0无超时机制）
        while (flag == 0);
    }

    @Override
    // 不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送
    public void udt_send(TCP_PACKET stcpPack) {
        // 设置错误控制标志（根据eFlag控制错误类型）
        // eFlag=0：无错误（RDT1.0）
        // eFlag=1：只出错（RDT2.0需要）
        // 这里可以根据实验需要设置不同的eFlag值
        tcpH.setTh_eflag((byte)1);  // 设置为出错模式

        // 发送数据报
        client.send(stcpPack);
    }

    @Override
    public void waitACK() {
        if(!ackQueue.isEmpty()) {
            int currentAck = ackQueue.poll();
            if (currentAck == currentSeq) {
                System.out.println("Clear: " + currentSeq);
                flag = 1;  // 设置标志，结束等待
            } else if (currentAck == -1) {
                // 收到NAK（负确认），需要重传
                System.out.println("Received NAK, retransmit: " + currentSeq);
                udt_send(tcpPack);  // 重传数据包
                flag = 0;  // 继续保持等待状态
            } else {
                // 收到错误的ACK，可能是重复的ACK，需要特殊处理
                System.out.println("Wrong ACK received: " + currentAck + ", expected: " + currentSeq);
                // RDT2.1/2.2会处理这种情况
                flag = 0;
            }
        }
    }

    @Override
    // 接收到ACK报文：检查校验和，将确认号插入ack队列
    public void recv(TCP_PACKET recvPack) {
        // 检查ACK包的校验和
        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        if (computedChkSum == receivedChkSum) {
            // 校验和正确，处理ACK
            System.out.println("Receive ACK Number: " + recvPack.getTcpH().getTh_ack());
            ackQueue.add(recvPack.getTcpH().getTh_ack());
            System.out.println();

            // 处理ACK报文
            waitACK();
        } else {
            // 校验和错误，ACK包本身出错
            System.out.println("ACK packet corrupted! Computed: " + computedChkSum +
                    ", Received: " + receivedChkSum);
            // RDT2.1会处理ACK出错的情况
            // 当前RDT2.0不处理ACK错误，可能会进入死锁
        }
    }
}