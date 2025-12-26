package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.message.*;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;    // 待发送的TCP数据报
    private volatile int flag = 0;
    private int currentSeq = 1;     // 当前序列号
    private int lastSentSeq = 0;    // 最后发送的数据包序列号
    private int lastAckedSeq = 0;   // 最后确认的序列号

    // RDT 3.0 新增：使用工程提供的定时器和重传任务
    private UDT_Timer retransTimer;     // 重传定时器
    private UDT_RetransTask retransTask; // 重传任务
    private boolean timerRunning = false; // 定时器是否在运行
    private final int TIMEOUT_INTERVAL = 1000; // 超时时间1秒
    private final int MAX_RETRIES = 5;      // 最大重传次数
    private int retryCount = 0;     // 当前重传次数
    private TCP_PACKET[] sentPackets; // 已发送但未确认的数据包（用于重传）

    /* 构造函数 */
    public TCP_Sender() {
        super();    // 调用超类构造函数
        super.initTCP_Sender(this);     // 初始化TCP发送端
        lastAckedSeq = 0;
        retryCount = 0;
        retransTimer = new UDT_Timer(); // 创建定时器
        sentPackets = new TCP_PACKET[10]; // 假设最多缓存10个未确认包
    }

    @Override
    // 可靠发送（应用层调用）：封装应用层数据，产生TCP数据报
    public void rdt_send(int dataIndex, int[] appData) {

        // 重置重传计数
        retryCount = 0;

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
        flag = 0;

        // 缓存当前发送的数据包（用于重传）
        sentPackets[currentSeq % 10] = tcpPack;

        // 发送TCP数据报
        udt_send(tcpPack);

        // RDT 3.0: 启动定时器（使用工程提供的定时器）
        startTimer();

        // 等待ACK报文
        waitForAck();
    }

    // 新增：等待ACK方法（RDT 3.0使用定时器机制）
    private void waitForAck() {
        System.out.println("Waiting for ACK for seq: " + lastSentSeq);

        long startTime = System.currentTimeMillis();
        final long MAX_WAIT_TIME = TIMEOUT_INTERVAL * MAX_RETRIES * 2; // 最大等待时间

        while (flag == 0) {
            // 检查是否超过最大等待时间
            if (System.currentTimeMillis() - startTime > MAX_WAIT_TIME) {
                System.out.println("Max wait time reached for seq: " + lastSentSeq + ", giving up.");
                stopTimer(); // 停止定时器
                flag = 1; // 强制结束等待
                break;
            }

            // 短暂休眠避免100% CPU占用
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 收到ACK，停止定时器
        stopTimer();
        System.out.println("ACK received for seq: " + lastSentSeq + ", transmission successful.");
    }

    // 新增：启动定时器（使用UDT_Timer和UDT_RetransTask）
    private void startTimer() {
        if (!timerRunning) {
            timerRunning = true;

            // 创建重传任务
            retransTask = new UDT_RetransTask(client, tcpPack);

            // 启动定时器：1秒后开始执行，之后每1秒执行一次
            retransTimer.schedule(retransTask, TIMEOUT_INTERVAL, TIMEOUT_INTERVAL);

            System.out.println("Timer started for seq: " + lastSentSeq);
        }
    }

    // 新增：停止定时器
    private void stopTimer() {
        if (timerRunning && retransTimer != null) {
            retransTimer.cancel();
            retransTimer = new UDT_Timer(); // 创建新的定时器实例
            timerRunning = false;
            System.out.println("Timer stopped for seq: " + lastSentSeq);
        }
    }

    @Override
    // 不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送
    public void udt_send(TCP_PACKET stcpPack) {
        // 设置错误控制标志
        tcpH.setTh_eflag((byte)4);  //

        // 发送数据报
        client.send(stcpPack);
    }

    @Override
    public void waitACK() {
        if(!ackQueue.isEmpty()) {
            int currentAck = ackQueue.poll();

            System.out.println("Processing ACK: " + currentAck +
                    ", lastSentSeq: " + lastSentSeq +
                    ", lastAckedSeq: " + lastAckedSeq);

            // RDT 3.0: 处理ACK，支持ACK丢失和超时重传
            if (currentAck == lastSentSeq) {
                // 收到对当前包的ACK
                System.out.println("Correct ACK received for seq: " + currentAck);
                lastAckedSeq = currentAck;
                flag = 1;  // 设置标志，结束等待
            }
             else if (currentAck < lastSentSeq && currentAck > 0) {
                // 收到旧的ACK（可能是延迟的ACK），忽略
                System.out.println("Received old/delayed ACK: " + currentAck +
                        ", current expected: " + lastSentSeq);
            } else {
                // 收到其他ACK（乱序或错误）
                System.out.println("Unexpected ACK: " + currentAck);
            }
        }
    }

    // 新增：从缓冲区查找指定序列号的包
    private TCP_PACKET findPacketInBuffer(int seq) {
        // 简单实现：在sentPackets数组中查找
        for (TCP_PACKET packet : sentPackets) {
            if (packet != null && packet.getTcpH().getTh_seq() == seq) {
                return packet;
            }
        }
        return null;
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
            // RDT 3.0: ACK损坏，不重传数据包，等待超时
            System.out.println("ACK corrupted, waiting for timeout retransmission...");
        }
    }
}