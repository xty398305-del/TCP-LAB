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

        // RDT2.0：检查校验和，如果出错发送NAK
        if(computedChkSum == receivedChkSum) {
            // 校验和正确，生成ACK报文段
            System.out.println("Checksum correct for packet: " + recvPack.getTcpH().getTh_seq());

            // 检查序列号是否正确（按序接收）
            if (recvPack.getTcpH().getTh_seq() == sequence) {
                // 序列号正确，生成正常ACK
                tcpH.setTh_ack(recvPack.getTcpH().getTh_seq());
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());

                // 计算ACK包的校验和
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));

                // 回复ACK报文段
                reply(ackPack);

                // 将接收到的正确有序的数据插入data队列，准备交付
                dataQueue.add(recvPack.getTcpS().getData());
                sequence++;
            } else {
                // 序列号错误，可能是重复包或乱序包
                System.out.println("Wrong sequence number. Expected: " + sequence +
                        ", Received: " + recvPack.getTcpH().getTh_seq());
                // RDT2.0：对于序列号错误，仍然发送ACK确认最新收到的正确包
                // 或者发送NAK？根据协议设计决定
                tcpH.setTh_ack(sequence - 1);  // 确认最新正确接收的包
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);
            }
        } else {
            // 校验和错误，发送NAK（负确认）
            System.out.println("Checksum error! Computed: " + computedChkSum +
                    ", Received: " + receivedChkSum);
            System.out.println("Problem: Packet Number: " + recvPack.getTcpH().getTh_seq() +
                    ", Expected: " + sequence);

            // 发送NAK（确认号为-1表示数据包出错）
            tcpH.setTh_ack(-1);
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
        tcpH.setTh_eflag((byte)0);  // ACK包设置为无错误

        // 发送数据报
        client.send(replyPack);
    }
}