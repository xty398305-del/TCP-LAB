package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.*;

import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;
    private int nextSeqNum = 1;
    private int baseSeq = 1;
    private final int WINDOW_SIZE = 4;
    // 在TCP_Sender类中添加
    private int duplicateAckCount = 0;

    // 简化的窗口管理
    private LinkedList<TCP_PACKET> sentPackets;  // 已发送但未确认的包
    private Timer timer;

    // 调试信息
    private int sendCount = 0;
    private int ackCount = 0;
    private int timeoutCount = 0;

    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
        sentPackets = new LinkedList<>();
        timer = new Timer();
        ackQueue = new LinkedList<>();

        System.out.println("=== TCP Sender Initialized ===");
        System.out.println("Window Size: " + WINDOW_SIZE);
    }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        sendCount++;
        System.out.println("\n=== SEND OPERATION #" + sendCount + " ===");
        System.out.println("Current state: baseSeq=" + baseSeq + ", nextSeqNum=" + nextSeqNum);
        System.out.println("Window size: " + sentPackets.size() + "/" + WINDOW_SIZE);

        // 检查窗口是否已满
        if (sentPackets.size() >= WINDOW_SIZE) {
            System.out.println("ERROR: Window full! Cannot send seq " + nextSeqNum);
            System.out.println("Window contents: " + getWindowContents());
            return;
        }

        // ✨ 关键修复：为每个包创建新的TCP头部对象！
        // 从父类复制默认的TCP头部设置
       // TCP_HEADER newHeader = new TCP_HEADER();

        // 在rdt_send方法中创建包时
        TCP_HEADER newHeader = new TCP_HEADER();
        newHeader.setTh_seq(nextSeqNum);
        newHeader.setTh_ack(0);  // ACK号由接收方设置
        newHeader.setTh_eflag((byte)7);
// 复制其他必要的头部字段...

        TCP_SEGMENT newSegment = new TCP_SEGMENT();
        newSegment.setData(appData);

        tcpPack = new TCP_PACKET(newHeader, newSegment, destinAddr);

// 计算校验和
        short checksum = CheckSum.computeChkSum(tcpPack);
        newHeader.setTh_sum(checksum);

        // 添加到已发送队列
        sentPackets.add(tcpPack);

        System.out.println("Added packet seq=" + nextSeqNum + " to window");
        System.out.println("Window contents after adding: " + getWindowContents());

        // 发送包
        System.out.println("Sending packet seq=" + nextSeqNum);
        udt_send(tcpPack);

        // 如果这是第一个包，启动定时器
        if (sentPackets.size() == 1) {
            System.out.println("Starting timer for window base=" + baseSeq);
            startTimer();
        }

        // 更新下一个序列号
        nextSeqNum++;

        System.out.println("Final state: baseSeq=" + baseSeq + ", nextSeqNum=" + nextSeqNum);
    }

    private void startTimer() {
        // 取消现有定时器
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();

        // 启动新定时器
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeoutCount++;
                System.out.println("\n=== TIMEOUT #" + timeoutCount + " ===");
                handleTimeout();
            }
        }, 200);

        System.out.println("Timer started (will expire in 1 second)");
    }

    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        // 打印发送的包信息
        int seq = stcpPack.getTcpH().getTh_seq();
        System.out.println("[Network] Sending packet seq=" + seq + " to network");

        // 设置错误控制标志
        tcpH.setTh_eflag((byte)2);

        // 实际发送
        client.send(stcpPack);
    }

    private void handleTimeout() {
        System.out.println("=== TIMEOUT HANDLER ===");
        System.out.println("Current baseSeq: " + baseSeq);
        System.out.println("Window contents: " + getWindowContents());

        if (sentPackets.isEmpty()) {
            System.out.println("Window is empty, nothing to retransmit");
            return;
        }

        // ✨ 关键：重传所有在窗口中的包
        System.out.println("Go-Back-N: Retransmitting ALL packets in window");

        // 创建一个副本来遍历，避免并发修改问题
        LinkedList<TCP_PACKET> packetsToRetransmit = new LinkedList<>(sentPackets);

        for (TCP_PACKET packet : packetsToRetransmit) {
            int seq = packet.getTcpH().getTh_seq();
            System.out.println("Retransmitting seq=" + seq);

            // 重新计算校验和
            short checksum = CheckSum.computeChkSum(packet);
            packet.getTcpH().setTh_sum(checksum);

            udt_send(packet);
        }

        // 重启定时器
        System.out.println("Restarting timer after retransmission");
        startTimer();
    }

    @Override
    public void recv(TCP_PACKET recvPack) {
        ackCount++;
        System.out.println("\n=== RECEIVE ACK #" + ackCount + " ===");

        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        System.out.println("ACK checksum - Computed: " + computedChkSum + ", Received: " + receivedChkSum);

        if (computedChkSum == receivedChkSum) {
            int ackNum = recvPack.getTcpH().getTh_ack();
            System.out.println("Valid ACK received: " + ackNum);
            System.out.println("Current baseSeq: " + baseSeq);
            System.out.println("Window before ACK: " + getWindowContents());

            processAck(ackNum);
        } else {
            System.out.println("ERROR: ACK checksum failed! Discarding ACK.");
        }
    }

    private void processAck(int ackNum) {
        System.out.println("Processing ACK=" + ackNum);
        System.out.println("Current baseSeq: " + baseSeq);
        System.out.println("Window contents: " + getWindowContents());

        //
        // 累积确认：所有序列号 <= ackNum 的包都已确认

        if (ackNum < baseSeq) {
            // 旧的ACK，忽略
            System.out.println("Old ACK (ackNum < baseSeq), ignoring");
            return;
        }

        // 累积确认：移除所有序列号 <= ackNum 的包
        int removedCount = 0;
        while (!sentPackets.isEmpty()) {
            TCP_PACKET firstPacket = sentPackets.getFirst();
            int firstSeq = firstPacket.getTcpH().getTh_seq();

            if (firstSeq <= ackNum) {
                sentPackets.removeFirst();
                removedCount++;
                System.out.println("Removed confirmed packet seq=" + firstSeq);
            } else {
                break;
            }
        }

        System.out.println("Removed " + removedCount + " packets from window");

        // 重置重复ACK计数
        duplicateAckCount = 0;

        // 更新baseSeq
        if (!sentPackets.isEmpty()) {
            baseSeq = sentPackets.getFirst().getTcpH().getTh_seq();
            System.out.println("New baseSeq (from window): " + baseSeq);

            // 重启定时器
            System.out.println("Restarting timer for new baseSeq=" + baseSeq);
            startTimer();
        } else {
            baseSeq = nextSeqNum;
            System.out.println("Window empty, new baseSeq (nextSeqNum): " + baseSeq);

            // 停止定时器
            System.out.println("Stopping timer (window empty)");
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }

        System.out.println("Window after ACK: " + getWindowContents());
    }

    @Override
    public void waitACK() {
        // 空实现
    }

    private String getWindowContents() {
        if (sentPackets.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (TCP_PACKET packet : sentPackets) {
            int seq = packet.getTcpH().getTh_seq();
            sb.append(seq).append(" ");
        }
        sb.deleteCharAt(sb.length() - 1);  // 移除最后一个空格
        sb.append("]");
        return sb.toString();
    }

    private String getWindowSequences() {
        StringBuilder sb = new StringBuilder("[");
        int expectedCount = 0;
        for (int seq = baseSeq; seq < nextSeqNum && expectedCount < WINDOW_SIZE; seq++) {
            sb.append(seq).append(" ");
            expectedCount++;
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    // 添加一个方法来打印完整状态
    private void printFullState() {
        System.out.println("\n=== FULL STATE ===");
        System.out.println("baseSeq: " + baseSeq);
        System.out.println("nextSeqNum: " + nextSeqNum);
        System.out.println("Window size: " + sentPackets.size() + "/" + WINDOW_SIZE);
        System.out.println("Window contents: " + getWindowContents());
        System.out.println("Expected window: " + getWindowSequences());
        System.out.println("=== END STATE ===");
    }
}