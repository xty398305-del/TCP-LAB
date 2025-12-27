package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.Timer;
import java.util.TimerTask;

public class SenderWindow {

    public Client client;
    private final int SenderWinSize = 32;       // 窗口大小
    private int windowPtr = 1;                  // 窗口基指针（最早的未确认包序号）
    private int firstPktPtr = 1;                // 第一个没有放入包的位置
    private TCP_PACKET[] packets = new TCP_PACKET[1000]; // 包缓冲区
    private Timer timer;
    private boolean timerRunning = false;

    public SenderWindow(Client client) {
        this.client = client;
    }

    // 判断窗口是否已满
    public boolean windowFull() {
        return (firstPktPtr - windowPtr) >= SenderWinSize;
    }

    // 将包放入窗口
    public void getPkts(TCP_PACKET packet) {
        int seq = packet.getTcpH().getTh_seq();
        packets[seq] = packet;

        System.out.println("Packet " + seq + " added to window buffer");

        // 如果是第一个包，启动定时器
        if (windowPtr == seq) {
            startRetransmissionTask();
        }

        // 更新firstPktPtr
        if (seq >= firstPktPtr) {
            firstPktPtr = seq + 1;
        }
    }

    // 接收到ACK
    public void recvACK(int CurSeq) {
        System.out.println("Processing ACK " + CurSeq + " in window: windowPtr=" +
                windowPtr + ", firstPktPtr=" + firstPktPtr);

        if (windowPtr <= CurSeq && CurSeq < windowPtr + SenderWinSize) {
            // 如果该ACK在发送窗口内

            // 滑动窗口：更新windowPtr
            int oldWindowPtr = windowPtr;
            windowPtr = CurSeq + 1;

            // 清理已确认的包（可选）
            for (int i = oldWindowPtr; i < windowPtr; i++) {
                packets[i] = null;
            }

            // 停止并重启定时器（如果还有未确认的包）
            stopTimer();
            if (windowPtr < firstPktPtr) {
                startRetransmissionTask();
            }

            System.out.println("Window slid from " + oldWindowPtr + " to " + windowPtr);
        } else {
            System.out.println("ACK " + CurSeq + " out of window range [" +
                    windowPtr + ", " + (windowPtr + SenderWinSize - 1) + "]");
        }
    }

    // 启动重传定时任务
    private void startRetransmissionTask() {
        if (!timerRunning) {
            timerRunning = true;
            timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    retransmitPackets();
                }
            };
            // 设置重传定时任务每1秒执行一次
            timer.schedule(timerTask, 1000, 1000);
            System.out.println("Retransmission timer started for windowPtr=" + windowPtr);
        }
    }

    // 停止定时器
    private void stopTimer() {
        if (timerRunning && timer != null) {
            timer.cancel();
            timer = null;
            timerRunning = false;
            System.out.println("Retransmission timer stopped");
        }
    }

    // 重传所有未确认的包
    public void retransmitPackets() {
        System.out.println("Retransmitting packets from " + windowPtr + " to " + (firstPktPtr - 1));

        int count = 0;
        for (int i = windowPtr; i < firstPktPtr; i++) {
            if (packets[i] != null) {
                client.send(packets[i]);
                count++;
                System.out.println("Retransmitted packet: " + i);
            }
        }

        if (count == 0) {
            System.out.println("No packets to retransmit");
        } else {
            System.out.println("Total retransmitted packets: " + count);
        }
    }
}