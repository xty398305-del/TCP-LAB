package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;
    private int expectedSeq = 1;
    private int lastAckSent = 0;

    // Tahoe接收方：按序接收
    private int lastDeliveredSeq = 0;  // 最近交付的序列号

    public TCP_Receiver() {
        super();
        super.initTCP_Receiver(this);
        expectedSeq = 1;
        lastAckSent = 0;
        dataQueue = new LinkedList<>();
    }

    @Override
    public void rdt_recv(TCP_PACKET recvPack) {
        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        int recvSeq = recvPack.getTcpH().getTh_seq();

        System.out.println("\n=== RECEIVER ===");
        System.out.println("Received seq=" + recvSeq + ", expected=" + expectedSeq);

        if(computedChkSum == receivedChkSum) {
            // 校验和正确
            System.out.println("Checksum correct for packet: " + recvSeq);

            // Tahoe: 累积确认，只确认按序到达的包
            if (recvSeq == expectedSeq) {
                // 按序到达
                System.out.println("In-order packet received: seq=" + recvSeq);

                // 1. 将数据插入data队列
                dataQueue.add(recvPack.getTcpS().getData());

                // 2. 更新期望序列号
                expectedSeq++;
                lastAckSent = recvSeq;

                // 3. 发送累积ACK
                sendAck(lastAckSent, recvPack.getSourceAddr());

                // 4. 尝试交付数据
                if(dataQueue.size() >= 20) {
                    deliver_data();
                }
            } else if (recvSeq > expectedSeq) {
                // 乱序到达，缓存或丢弃（这里简单实现为丢弃并发送重复ACK）
                System.out.println("Out-of-order packet, sending duplicate ACK for seq=" + (expectedSeq - 1));
                // 发送上一个正确接收包的ACK（重复ACK）
                sendAck(expectedSeq - 1, recvPack.getSourceAddr());
            } else {
                // 重复包
                System.out.println("Duplicate packet: seq=" + recvSeq);
                // 发送累积ACK
                sendAck(lastAckSent, recvPack.getSourceAddr());
            }
        } else {
            // 校验和错误
            System.out.println("Checksum error! Packet corrupted: " + recvSeq);
            // 发送上一个正确接收包的ACK
            sendAck(expectedSeq - 1, recvPack.getSourceAddr());
            lastAckSent = expectedSeq - 1;
        }
    }

    private void sendAck(int ackSeq, InetAddress destAddr) {
        if (ackSeq == lastAckSent && ackSeq > 0) {
            System.out.println("Duplicate ACK for seq: " + ackSeq);
        }

        tcpH.setTh_seq(0);
        tcpH.setTh_ack(ackSeq);

        ackPack = new TCP_PACKET(tcpH, tcpS, destAddr);
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);

        System.out.println("Sent ACK for seq: " + ackSeq);
    }

    @Override
    public void deliver_data() {
        File fw = new File("recvData.txt");
        BufferedWriter writer;

        try {
            writer = new BufferedWriter(new FileWriter(fw, true));

            while(!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();

                for(int i = 0; i < data.length; i++) {
                    writer.write(data[i] + "\n");
                }

                writer.flush();
            }
            writer.close();
            System.out.println("Data delivered to file, remaining in queue: " + dataQueue.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reply(TCP_PACKET replyPack) {
        tcpH.setTh_eflag((byte)7);
        client.send(replyPack);
    }
}