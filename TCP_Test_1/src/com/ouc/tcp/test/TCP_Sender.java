package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.*;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;    // 待发送的TCP数据报
    private volatile int flag = 0;
    private int currentSeq = 1;     // 当前序列号
    private int lastSentSeq = 0;    // 最后发送的数据包序列号
    private int lastAckedSeq = 0;   // 最后确认的序列号
    private int duplicateAckCount = 0; // 重复ACK计数

    /* 构造函数 */
    public TCP_Sender() {
        super();    // 调用超类构造函数
        super.initTCP_Sender(this);     // 初始化TCP发送端
        lastAckedSeq = 0;
        duplicateAckCount = 0;
    }

    @Override
    // 可靠发送（应用层调用）：封装应用层数据，产生TCP数据报
    public void rdt_send(int dataIndex, int[] appData) {

        // 生成TCP数据报（设置序号和数据字段/校验和）
        currentSeq = dataIndex * appData.length + 1;
        tcpH.setTh_seq(currentSeq);
        tcpS.setData(appData);
        tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);

        // 计算并设置校验和
        short checksum = CheckSum.computeChkSum(tcpPack);
        tcpH.setTh_sum(checksum);
        tcpPack.setTcpH(tcpH);

        lastSentSeq = currentSeq;
        duplicateAckCount = 0;

        // 发送TCP数据报
        udt_send(tcpPack);
        flag = 0;

        // 等待ACK报文（添加超时机制）
        waitForAckWithTimeout();
    }

    // 新增：带超时的等待ACK方法
    private void waitForAckWithTimeout() {
        long startTime = System.currentTimeMillis();
        final long TIMEOUT_MS = 5000; // 5秒超时
        final int MAX_RETRIES = 3;    // 最大重试次数
        int retryCount = 0;

        while (flag == 0 && retryCount < MAX_RETRIES) {
            // 检查快速重传条件
            if (duplicateAckCount >= 3) {
                System.out.println("Fast retransmit triggered for seq: " + lastSentSeq);
                udt_send(tcpPack);
                duplicateAckCount = 0;
                startTime = System.currentTimeMillis(); // 重置超时计时
                retryCount++;
            }

            // 检查超时
            if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                System.out.println("Timeout! Retransmitting seq: " + lastSentSeq);
                udt_send(tcpPack);
                startTime = System.currentTimeMillis();
                retryCount++;
            }

            // 短暂休眠避免100% CPU占用
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (retryCount >= MAX_RETRIES) {
            System.out.println("Max retries reached for seq: " + lastSentSeq);
            // 可以设置flag=1避免死锁，但这表示传输失败
            flag = 1;
        }
    }

    @Override
    // 不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送
    public void udt_send(TCP_PACKET stcpPack) {
        // 设置错误控制标志
        tcpH.setTh_eflag((byte)1);  // 设置为出错模式

        // 发送数据报
        client.send(stcpPack);
    }

    @Override
    public void waitACK() {
        if(!ackQueue.isEmpty()) {
            int currentAck = ackQueue.poll();

            // RDT2.2: 只处理ACK，没有NAK
            System.out.println("Processing ACK: " + currentAck +
                    ", lastSentSeq: " + lastSentSeq +
                    ", lastAckedSeq: " + lastAckedSeq);

            if (currentAck == lastSentSeq) {
                // 收到对当前包的ACK
                System.out.println("Correct ACK received for seq: " + currentAck);
                lastAckedSeq = currentAck;
                flag = 1;  // 设置标志，结束等待
                duplicateAckCount = 0; // 重置重复ACK计数
            } else if (currentAck == lastAckedSeq) {
                // 收到重复ACK
                duplicateAckCount++;
                System.out.println("Duplicate ACK #" + duplicateAckCount +
                        " for seq: " + currentAck);
                // RDT2.2: 收到重复ACK时不设置flag=1，继续等待
                // 当duplicateAckCount >= 3时会触发重传
            } else if (currentAck > lastSentSeq) {
                // 收到未来的ACK（不应该发生）
                System.out.println("Future ACK received: " + currentAck +
                        ", last sent seq: " + lastSentSeq);
            } else {
                // 收到其他ACK（乱序）
                System.out.println("Out-of-order ACK: " + currentAck);
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
            int ackNum = recvPack.getTcpH().getTh_ack();
            System.out.println("Receive ACK Number: " + ackNum);
            ackQueue.add(ackNum);
            System.out.println();

            // 处理ACK报文
            waitACK();
        } else {
            // 校验和错误，ACK包本身出错
            System.out.println("ACK packet corrupted! Computed: " + computedChkSum +
                    ", Received: " + receivedChkSum);
            // RDT2.2: ACK损坏时视为重复ACK
            if (lastAckedSeq > 0) {
                duplicateAckCount++;
                System.out.println("Treating corrupted ACK as duplicate #" + duplicateAckCount +
                        " for seq: " + lastAckedSeq);
            } else {
                // 这是第一个ACK就损坏了，直接重传
                System.out.println("First ACK corrupted, retransmitting seq: " + lastSentSeq);
                udt_send(tcpPack);
            }
        }
    }
}