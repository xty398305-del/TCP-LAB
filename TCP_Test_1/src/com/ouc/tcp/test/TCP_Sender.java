package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;
    private int nextSeqNum = 1;
    private int baseSeq = 1;

    // Reno协议：累积确认
    private int lastACKSeq = 0;  // 最近收到的ACK序号
    private int duplicateACKCount = 0;  // 重复ACK计数

    // Reno拥塞控制参数
    private int cwnd = 1;  // 拥塞窗口大小
    private int ssthresh = 8;  // 慢启动阈值（初始设为8）
    private int ConAck = 0;  // 累计ACK计数（用于拥塞避免）

    // Reno新增：快速恢复相关状态
    private boolean inFastRecovery = false;  // 是否处于快速恢复阶段
    private int recoveryPoint = 0;  // 进入快速恢复时的序列号（期望的下一个ACK）
    private int fastRecoveryCount = 0;  // 快速恢复阶段收到的ACK计数

    // 使用Map来管理每个包的状态
    private Map<Integer, TCP_PACKET> packets;
    private Timer globalTimer;  // 全局计时器

    // 调试信息
    private int sendCount = 0;
    private int ackCount = 0;
    private int timeoutCount = 0;

    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
        packets = new ConcurrentHashMap<>();
        ackQueue = new LinkedList<>();

        // 启动全局计时器
        startGlobalTimer();

        System.out.println("=== TCP Reno Sender Initialized ===");
        System.out.println("Initial cwnd=" + cwnd + ", ssthresh=" + ssthresh);
    }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        sendCount++;
        System.out.println("\n=== SEND OPERATION #" + sendCount + " ===");
        System.out.println("Current state: baseSeq=" + baseSeq +
                ", nextSeqNum=" + nextSeqNum +
                ", cwnd=" + cwnd +
                ", ssthresh=" + ssthresh +
                ", inFastRecovery=" + inFastRecovery);

        // 检查是否在拥塞窗口内
        if (nextSeqNum - baseSeq >= cwnd) {
            System.out.println("Window full! Cannot send seq " + nextSeqNum +
                    " (cwnd=" + cwnd + ")");
            return;
        }

        // 创建包
        TCP_HEADER newHeader = new TCP_HEADER();
        newHeader.setTh_seq(nextSeqNum);
        newHeader.setTh_ack(0);
        newHeader.setTh_eflag((byte)7);

        TCP_SEGMENT newSegment = new TCP_SEGMENT();
        newSegment.setData(appData);

        tcpPack = new TCP_PACKET(newHeader, newSegment, destinAddr);

        // 计算校验和
        short checksum = CheckSum.computeChkSum(tcpPack);
        newHeader.setTh_sum(checksum);

        // 保存包
        packets.put(nextSeqNum, tcpPack);

        // 记录发送时间
        System.out.println("Sending packet seq=" + nextSeqNum +
                " (cwnd=" + cwnd + ", ssthresh=" + ssthresh + ")");

        // 发送包
        udt_send(tcpPack);

        // 更新下一个序列号
        nextSeqNum++;
    }

    // 启动全局计时器
    private void startGlobalTimer() {
        globalTimer = new Timer();
        globalTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkTimeout();
            }
        }, 0, 800);  // 每600ms检查一次超时
    }

    // 检查超时
    private void checkTimeout() {
        boolean hasTimeout = false;

        // 遍历所有未确认的包（序号大于lastACKSeq）
        for (int seq : packets.keySet()) {
            if (seq > lastACKSeq) {  // 只检查未确认的包
                hasTimeout = true;  // 假设有超时
                System.out.println("Timeout for seq: " + seq);
                TCP_PACKET packet = packets.get(seq);
                client.send(packet);  // 重传
            }
        }

        // 只有当存在超时的包时才进行窗口重置
        if (hasTimeout) {
            timeoutCount++;
            System.out.println("\n=== RENO: Timeout detected, reducing window ===");
            System.out.println("cwnd " + cwnd + " to 1");
            System.out.println("ssthresh " + ssthresh + " to " + Math.max(cwnd / 2, 2));

            // Reno: 发生超时，ssthresh设为当前cwnd的一半（至少为2），cwnd重置为1
            ssthresh = Math.max(cwnd / 2, 2);
            cwnd = 1;
            ConAck = 0;  // 重置累计ACK计数

            // 退出快速恢复状态（如果正在快速恢复）
            if (inFastRecovery) {
                inFastRecovery = false;
                recoveryPoint = 0;
                fastRecoveryCount = 0;
                System.out.println("Exiting fast recovery due to timeout");
            }
        }
    }

    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        int seq = stcpPack.getTcpH().getTh_seq();
        System.out.println("[Network] Sending packet seq=" + seq +
                " (cwnd=" + cwnd + ", ssthresh=" + ssthresh +
                ", inFastRecovery=" + inFastRecovery + ")");
        client.send(stcpPack);
    }

    @Override
    public void recv(TCP_PACKET recvPack) {
        ackCount++;
        System.out.println("\n=== RECEIVE ACK #" + ackCount + " ===");

        short computedChkSum = CheckSum.computeChkSum(recvPack);
        short receivedChkSum = recvPack.getTcpH().getTh_sum();

        System.out.println("ACK checksum - Computed: " + computedChkSum +
                ", Received: " + receivedChkSum);

        if (computedChkSum == receivedChkSum) {
            int ackNum = recvPack.getTcpH().getTh_ack();
            System.out.println("Valid ACK received: " + ackNum);
            System.out.println("Current baseSeq: " + baseSeq);
            System.out.println("Current cwnd=" + cwnd + ", ssthresh=" + ssthresh +
                    ", inFastRecovery=" + inFastRecovery);

            processAck(ackNum);
        } else {
            System.out.println("ERROR: ACK checksum failed! Discarding ACK.");
        }
    }

    private void processAck(int ackNum) {
        System.out.println("Processing ACK=" + ackNum);

        if (inFastRecovery) {
            // Reno: 处于快速恢复阶段
            processAckInFastRecovery(ackNum);
        } else {
            // 正常阶段
            processAckNormal(ackNum);
        }

        System.out.println("New baseSeq: " + baseSeq);
        System.out.println("New cwnd=" + cwnd + ", ssthresh=" + ssthresh +
                ", inFastRecovery=" + inFastRecovery);
    }

    private void processAckNormal(int ackNum) {
        // Reno: 累积确认
        if (ackNum > lastACKSeq) {
            // 新的ACK，更新lastACKSeq
            lastACKSeq = ackNum;
            duplicateACKCount = 0;  // 重置重复ACK计数

            // 更新baseSeq
            if (ackNum >= baseSeq) {
                // 移除已确认的包
                Iterator<Integer> it = packets.keySet().iterator();
                while (it.hasNext()) {
                    int seq = it.next();
                    if (seq <= ackNum) {
                        it.remove();
                    }
                }
                baseSeq = ackNum + 1;
            }

            // Reno拥塞控制（正常阶段）
            updateCongestionWindow();

        } else if (ackNum == lastACKSeq) {
            // 重复ACK
            duplicateACKCount++;
            System.out.println("Duplicate ACK #" + duplicateACKCount + " for seq=" + ackNum);

            // Reno: 3个重复ACK触发快速重传和快速恢复
            if (duplicateACKCount == 3) {
                System.out.println("\n=== RENO: Fast Retransmit and Recovery for seq=" + (ackNum + 1) + " ===");
                enterFastRecovery(ackNum + 1);
            } else if (duplicateACKCount > 3) {
                // 已经有3个以上重复ACK，继续快速重传
                System.out.println("Additional duplicate ACK in normal state: #" + duplicateACKCount);
            }
        }
    }

    private void processAckInFastRecovery(int ackNum) {
        // Reno: 快速恢复阶段的ACK处理
        if (ackNum > lastACKSeq) {
            // 收到新的ACK（可能是重传包的ACK）
            if (ackNum >= recoveryPoint) {
                // 收到了期望的新ACK，退出快速恢复
                System.out.println("Received new ACK in fast recovery, exiting fast recovery");

                // 退出快速恢复：cwnd设为ssthresh
                cwnd = ssthresh;
                inFastRecovery = false;
                recoveryPoint = 0;
                fastRecoveryCount = 0;

                // 更新lastACKSeq
                lastACKSeq = ackNum;
                duplicateACKCount = 0;

                // 更新baseSeq
                if (ackNum >= baseSeq) {
                    // 移除已确认的包
                    Iterator<Integer> it = packets.keySet().iterator();
                    while (it.hasNext()) {
                        int seq = it.next();
                        if (seq <= ackNum) {
                            it.remove();
                        }
                    }
                    baseSeq = ackNum + 1;
                }
            } else {
                // 仍然在快速恢复中，但收到了部分确认
                System.out.println("Partial ACK in fast recovery: " + ackNum);

                // 重传下一个期望的包
                int nextExpectedSeq = ackNum + 1;
                if (packets.containsKey(nextExpectedSeq)) {
                    TCP_PACKET packet = packets.get(nextExpectedSeq);
                    System.out.println("Fast retransmitting seq=" + nextExpectedSeq + " after partial ACK");

                    short checksum = CheckSum.computeChkSum(packet);
                    packet.getTcpH().setTh_sum(checksum);

                    udt_send(packet);
                }
            }
        } else if (ackNum == lastACKSeq) {
            // 在快速恢复阶段收到重复ACK
            fastRecoveryCount++;
            System.out.println("Duplicate ACK in fast recovery: #" + fastRecoveryCount);

            // Reno: 在快速恢复阶段，每收到一个重复ACK，cwnd加1
            // 这允许发送新数据包，保持数据流
            cwnd++;
            System.out.println("Fast Recovery: cwnd increased to " + cwnd);

            // 尝试发送新数据（如果有）
            System.out.println("Window size in fast recovery: " + (nextSeqNum - baseSeq) + "/" + cwnd);
        }
    }

    // 进入快速恢复阶段（Reno特有）
    private void enterFastRecovery(int seqNum) {
        // 1. 设置慢启动阈值
        ssthresh = Math.max(cwnd / 2, 2);

        // 2. 重传丢失的包
        fastRetransmit(seqNum);

        // 3. 进入快速恢复阶段
        inFastRecovery = true;
        recoveryPoint = seqNum;  // 期望的下一个序列号
        fastRecoveryCount = 0;

        // 4. 调整拥塞窗口：cwnd = ssthresh + 3（因为有3个重复ACK）
        cwnd = ssthresh + 3;
        System.out.println("Entering fast recovery: cwnd set to " + cwnd + " (ssthresh=" + ssthresh + ")");
    }

    // Reno拥塞窗口更新（只在正常阶段调用）
    private void updateCongestionWindow() {
        // 判断当前处于慢启动阶段还是拥塞避免阶段
        if (cwnd < ssthresh) { // 如果拥塞窗口小于慢启动阈值，执行慢启动
            System.out.println("Slow Start: cwnd " + cwnd + " -> " + (cwnd + 1));
            cwnd++; // 每收到一个新ACK，窗口大小加1，实现指数级增长
        } else { // 如果已经超过慢启动阈值，进入拥塞避免阶段
            ConAck++; // 累计ACK计数
            if (ConAck >= cwnd) { // 累计收到的ACK数达到窗口大小
                ConAck -= cwnd; // 减去当前窗口大小，重置计数
                System.out.println("Congestion Avoidance: cwnd " + cwnd + " -> " + (cwnd + 1));
                cwnd++; // 窗口以线性增长方式增加1
            }
        }
    }

    // 快速重传
    private void fastRetransmit(int seqNum) {
        if (packets.containsKey(seqNum)) {
            TCP_PACKET packet = packets.get(seqNum);
            System.out.println("Fast retransmitting seq=" + seqNum);

            // 重新计算校验和
            short checksum = CheckSum.computeChkSum(packet);
            packet.getTcpH().setTh_sum(checksum);

            udt_send(packet);
        }
    }

    @Override
    public void waitACK() {
        // 空实现
    }

    // 获取当前窗口状态（调试用）
    private String getWindowState() {
        StringBuilder sb = new StringBuilder("[");
        for (int seq = baseSeq; seq < nextSeqNum; seq++) {
            if (seq > baseSeq) sb.append(" ");
            sb.append(seq);
            if (seq <= lastACKSeq) {
                sb.append("(A)");  // A表示已确认
            }
        }
        sb.append("]");
        return sb.toString();
    }
}