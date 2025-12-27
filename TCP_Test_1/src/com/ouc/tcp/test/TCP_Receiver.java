package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;
    private int expectedSeq = 1;
    private int lastAckSent = 0; // 记录最后发送的ACK号

    public TCP_Receiver() {
        super();
        super.initTCP_Receiver(this);
        expectedSeq = 1;
        lastAckSent = 0;
        dataQueue = new java.util.LinkedList<>();
    }

    @Override
    public void rdt_recv(TCP_PACKET recvPack) {
        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        int recvSeq = recvPack.getTcpH().getTh_seq();

        System.out.println("Receiver: received seq=" + recvSeq +
                ", expected=" + expectedSeq);

        if(computedChkSum == receivedChkSum) {
            // 校验和正确
            System.out.println("Checksum correct for packet: " + recvSeq);

            if (recvSeq == expectedSeq) {
                // 1. 将数据插入data队列
                dataQueue.add(recvPack.getTcpS().getData());

                // 2. 更新期望序列号
                expectedSeq++;

                // 3. ✨ 关键：发送已确认的包序号（recvSeq）
                sendAck(recvSeq, recvPack.getSourceAddr());
                lastAckSent = recvSeq;

                System.out.println("In-order packet received, new expectedSeq: " + expectedSeq);

                // 4. 尝试交付数据
                if(dataQueue.size() >= 20) {
                    deliver_data();
                }
            } else if (recvSeq < expectedSeq) {
                // 重复的旧包
                System.out.println("Duplicate packet: " + recvSeq +
                        " (already received, expected: " + expectedSeq + ")");

                // 发送已确认的最高序列号
                sendAck(expectedSeq - 1, recvPack.getSourceAddr());
                lastAckSent = expectedSeq - 1;
            } else {
                // 乱序到达的分组
                System.out.println("Out-of-order packet: " + recvSeq +
                        " (expected: " + expectedSeq + ")");

                // Go-Back-N: 发送重复ACK（确认最后一个按序接收的分组）
                sendAck(expectedSeq - 1, recvPack.getSourceAddr());
                lastAckSent = expectedSeq - 1;
            }
        } else {
            // 校验和错误
            System.out.println("Checksum error! Packet corrupted: " + recvSeq);

            // 发送重复ACK
            sendAck(expectedSeq - 1, recvPack.getSourceAddr());
            lastAckSent = expectedSeq - 1;
        }

        System.out.println();
    }

    private void sendAck(int ackSeq, InetAddress destAddr) {
        // 避免重复发送相同的ACK
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