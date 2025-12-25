package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;    // 回复的ACK报文段
    int sequence = 1;               // 用于记录当前待接收的包序号
    int lastCorrectSeq = 0;         // 最后正确接收的序列号

    /* 构造函数 */
    public TCP_Receiver() {
        super();    // 调用超类构造函数
        super.initTCP_Receiver(this);   // 初始化TCP接收端
        lastCorrectSeq = 0;
    }

    @Override
    // 接收到数据报：检查校验和，设置回复的ACK报文段
    public void rdt_recv(TCP_PACKET recvPack) {
        // 计算校验和
        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        // 获取接收到的序列号
        int recvSeq = recvPack.getTcpH().getTh_seq();

        // RDT2.2: 总是发送ACK，没有NAK
        // ACK号 = 最后正确接收的序列号

        System.out.println("Receiver: seq=" + recvSeq +
                ", expected=" + sequence +
                ", lastCorrect=" + lastCorrectSeq);

        if(computedChkSum == receivedChkSum) {
            // 校验和正确
            System.out.println("Checksum correct for packet: " + recvSeq);

            if (recvSeq == sequence) {
                // 序列号正确，接收数据
                System.out.println("In-order packet received: " + recvSeq);

                // 将接收到的正确有序的数据插入data队列，准备交付
                dataQueue.add(recvPack.getTcpS().getData());
                lastCorrectSeq = recvSeq; // 更新最后正确接收的序列号
                sequence += 100; // 更新期望序列号

                // 发送ACK
                sendAck(lastCorrectSeq, recvPack.getSourceAddr()); // 添加目标地址
            } else if (recvSeq < sequence) {
                // 收到旧的重复包
                System.out.println("Duplicate packet received. Seq: " + recvSeq +
                        ", Expected: " + sequence);
                // 发送重复ACK（确认最后正确接收的包）
                sendAck(lastCorrectSeq, recvPack.getSourceAddr()); // 添加目标地址
            } else {
                // 收到未来的包（乱序）
                System.out.println("Out-of-order packet. Seq: " + recvSeq +
                        ", Expected: " + sequence);
                // 发送重复ACK（确认最后正确接收的包）
                sendAck(lastCorrectSeq, recvPack.getSourceAddr()); // 添加目标地址
            }
        } else {
            // 校验和错误
            System.out.println("Checksum error! Computed: " + computedChkSum +
                    ", Received: " + receivedChkSum);
            System.out.println("Problem: Packet Number: " + recvSeq +
                    ", Expected: " + sequence);

            // RDT2.2: 校验和错误时发送重复ACK（而不是NAK）
            sendAck(lastCorrectSeq, recvPack.getSourceAddr()); // 添加目标地址
        }

        System.out.println();

        // 交付数据（每20组数据交付一次）
        if(dataQueue.size() >= 20) {
            deliver_data();
        }
    }

    // 新增：发送ACK的辅助方法
    private void sendAck(int ackSeq, java.net.InetAddress destAddr) {
        // 设置ACK包的序列号（不重要）和确认号
        tcpH.setTh_seq(0); // ACK包序列号设为0
        tcpH.setTh_ack(ackSeq); // 确认号为最后正确接收的序列号

        // 修复：传入目标地址
        ackPack = new TCP_PACKET(tcpH, tcpS, destAddr);

        // 计算ACK包的校验和
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));

        // 回复ACK报文段
        reply(ackPack);

        System.out.println("Sent ACK: " + ackSeq);
    }

    @Override
    // 交付数据（将数据写入文件）
    public void deliver_data() {
        // 检查dataQueue，将数据写入文件
        File fw = new File("recvData.txt");
        BufferedWriter writer;

        try {
            writer = new BufferedWriter(new FileWriter(fw, true));

            // 循环检查data队列中是否有新交付数据
            while(!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();

                // 将数据写入文件
                for(int i = 0; i < data.length; i++) {
                    writer.write(data[i] + "\n");
                }

                writer.flush();     // 清空输出缓存
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    // 回复ACK报文段
    public void reply(TCP_PACKET replyPack) {
        // 设置错误控制标志
        // RDT2.2: ACK包也会出错，设置为出错模式
        tcpH.setTh_eflag((byte)4);

        // 发送数据报
        client.send(replyPack);
    }
}