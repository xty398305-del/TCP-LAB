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

    // SR协议：缓存乱序到达的包
    private Map<Integer, TCP_PACKET> outOfOrderPackets;
    private final int RECEIVE_WINDOW_SIZE = 4;

    public TCP_Receiver() {
        super();
        super.initTCP_Receiver(this);
        expectedSeq = 1;
        lastAckSent = 0;
        dataQueue = new LinkedList<>();
        outOfOrderPackets = new HashMap<>();
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

            // SR协议：发送该包的ACK（无论是否按序）
            sendAck(recvSeq, recvPack.getSourceAddr());
            lastAckSent = recvSeq;

            if (recvSeq == expectedSeq) {
                // 按序到达
                System.out.println("In-order packet received: seq=" + recvSeq);

                // 1. 将数据插入data队列
                dataQueue.add(recvPack.getTcpS().getData());

                // 2. 更新期望序列号
                expectedSeq++;

                // 3. 检查是否有缓存的包可以按序交付
                checkAndDeliverCachedPackets();

                // 4. 尝试交付数据
                if(dataQueue.size() >= 20) {
                    deliver_data();
                }
            } else if (recvSeq > expectedSeq && recvSeq < expectedSeq + RECEIVE_WINDOW_SIZE) {
                // 乱序但在窗口内，缓存它
                System.out.println("Out-of-order packet within window, caching: seq=" + recvSeq);
                outOfOrderPackets.put(recvSeq, recvPack);
            } else if (recvSeq < expectedSeq) {
                // 重复包
                System.out.println("Duplicate packet: seq=" + recvSeq);
                // 仍然发送ACK，但不需要处理数据
            } else {
                // 超出接收窗口
                System.out.println("Packet outside receive window, ignoring: seq=" + recvSeq);
            }
        } else {
            // 校验和错误
            System.out.println("Checksum error! Packet corrupted: " + recvSeq);

            // SR协议：不发送ACK给损坏的包
            // 或者发送前一个正确包的ACK（根据具体实现）
            sendAck(expectedSeq - 1, recvPack.getSourceAddr());
            lastAckSent = expectedSeq - 1;
        }
    }

    // 检查缓存的包是否可以按序交付
    private void checkAndDeliverCachedPackets() {
        while (outOfOrderPackets.containsKey(expectedSeq)) {
            System.out.println("Delivering cached packet: seq=" + expectedSeq);
            TCP_PACKET cachedPacket = outOfOrderPackets.remove(expectedSeq);
            dataQueue.add(cachedPacket.getTcpS().getData());
            expectedSeq++;
        }
        System.out.println("New expectedSeq after delivering cached: " + expectedSeq);
    }

    private void sendAck(int ackSeq, InetAddress destAddr) {
        if (ackSeq == lastAckSent) {
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