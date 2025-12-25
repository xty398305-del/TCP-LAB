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



    /* 构造函数 */
    public TCP_Receiver() {
        super();    // 调用超类构造函数
        super.initTCP_Receiver(this);   // 初始化TCP接收端
    }

    @Override
    // 接收到数据报：检查校验和，设置回复的ACK报文段
    public void rdt_recv(TCP_PACKET recvPack) {
        // 计算校验和
        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        // 获取接收到的序列号
        int recvSeq = recvPack.getTcpH().getTh_seq();

        // RDT2.1：检查校验和，如果出错发送带序列号的NAK
        if(computedChkSum == receivedChkSum) {
            // 校验和正确，生成ACK报文段
            System.out.println("Checksum correct for packet: " + recvSeq);

            // 检查序列号是否正确（按序接收）
            if (recvSeq == sequence) {
                // 序列号正确，生成正常ACK（正数表示ACK）
                tcpH.setTh_ack(recvSeq);  // ACK序列号等于接收到的序列号
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());

                // 计算ACK包的校验和
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));

                // 回复ACK报文段
                reply(ackPack);

                // 将接收到的正确有序的数据插入data队列，准备交付
                dataQueue.add(recvPack.getTcpS().getData());
                sequence += 100;
            } else if (recvSeq < sequence) {
                // 收到旧的重复包，发送ACK确认这个包
                System.out.println("Duplicate packet received. Seq: " + recvSeq +
                        ", Expected: " + sequence);
                tcpH.setTh_ack(recvSeq);  // ACK序列号等于重复包的序列号
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);
                // 不交付数据，因为这是重复包
            } else {
                // 收到未来的包（乱序），发送ACK确认最新正确接收的包
                System.out.println("Out-of-order packet. Seq: " + recvSeq +
                        ", Expected: " + sequence);
                tcpH.setTh_ack(sequence - 1);  // ACK最新正确接收的包
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);
            }
        } else {
            // 校验和错误，发送带序列号的NAK（用负数表示NAK）
            System.out.println("Checksum error! Computed: " + computedChkSum +
                    ", Received: " + receivedChkSum);
            System.out.println("Problem: Packet Number: " + recvSeq +
                    ", Expected: " + sequence);

            // 发送带序列号的NAK（用负的序列号表示NAK）
            tcpH.setTh_ack(-recvSeq);  // 负号表示NAK，绝对值是出错的序列号
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
            reply(ackPack);
        }

        System.out.println();


        // 交付数据（每20组数据交付一次）
        if(dataQueue.size() == 20)
            deliver_data();
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
        tcpH.setTh_eflag((byte)1);  // ACK包设置为错误

        // 发送数据报
        client.send(replyPack);
    }
}