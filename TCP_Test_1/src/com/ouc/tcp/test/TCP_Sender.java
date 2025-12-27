package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;
    private int nextSeqNum = 1;
    private int baseSeq = 1;
    private final int WINDOW_SIZE = 4;

    // SR协议：为每个包维护单独的状态
    private static class PacketState {
        TCP_PACKET packet;
        Timer timer;
        boolean acked;

        PacketState(TCP_PACKET packet) {
            this.packet = packet;
            this.acked = false;
        }
    }

    // 使用Map来管理每个包的状态
    private Map<Integer, PacketState> sentPackets;
    // 已确认的包的集合
    private Set<Integer> ackedPackets;

    // 调试信息
    private int sendCount = 0;
    private int ackCount = 0;
    private int timeoutCount = 0;

    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
        sentPackets = new ConcurrentHashMap<>();
        ackedPackets = new HashSet<>();
        ackQueue = new LinkedList<>();

        System.out.println("=== SR Sender Initialized ===");
        System.out.println("Window Size: " + WINDOW_SIZE);
    }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        sendCount++;
        System.out.println("\n=== SEND OPERATION #" + sendCount + " ===");
        System.out.println("Current state: baseSeq=" + baseSeq + ", nextSeqNum=" + nextSeqNum);
        System.out.println("Window size: " + getWindowSize() + "/" + WINDOW_SIZE);

        // 检查窗口是否已满
        if (getWindowSize() >= WINDOW_SIZE) {
            System.out.println("ERROR: Window full! Cannot send seq " + nextSeqNum);
            System.out.println("Window contents: " + getWindowContents());
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

        // 添加到已发送但未确认的包中
        PacketState state = new PacketState(tcpPack);
        sentPackets.put(nextSeqNum, state);

        // 启动该包的定时器
        startTimerForPacket(nextSeqNum);

        System.out.println("Added packet seq=" + nextSeqNum + " to window");
        System.out.println("Window contents: " + getWindowContents());

        // 发送包
        System.out.println("Sending packet seq=" + nextSeqNum);
        udt_send(tcpPack);

        // 更新下一个序列号
        nextSeqNum++;

        System.out.println("Final state: baseSeq=" + baseSeq + ", nextSeqNum=" + nextSeqNum);
    }

    // 启动单个包的定时器
    private void startTimerForPacket(int seqNum) {
        PacketState state = sentPackets.get(seqNum);
        if (state == null) return;

        // 如果已有定时器，先取消
        if (state.timer != null) {
            state.timer.cancel();
        }

        state.timer = new Timer();
        state.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("\n=== TIMEOUT for packet seq=" + seqNum + " ===");
                handlePacketTimeout(seqNum);
            }
        }, 200);  // 超时时间200ms

        System.out.println("Started timer for packet seq=" + seqNum);
    }

    // 停止包的定时器
    private void stopTimerForPacket(int seqNum) {
        PacketState state = sentPackets.get(seqNum);
        if (state != null && state.timer != null) {
            state.timer.cancel();
            state.timer = null;
            System.out.println("Stopped timer for packet seq=" + seqNum);
        }
    }

    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        int seq = stcpPack.getTcpH().getTh_seq();
        System.out.println("[Network] Sending packet seq=" + seq + " to network");
        client.send(stcpPack);
    }

    // SR协议：只重传超时的单个包
    private void handlePacketTimeout(int seqNum) {
        PacketState state = sentPackets.get(seqNum);
        if (state == null || state.acked) {
            return;
        }

        timeoutCount++;
        System.out.println("\n=== SELECTIVE REPEAT: Retransmitting only packet seq=" + seqNum + " ===");

        // 重传该包
        System.out.println("Retransmitting seq=" + seqNum);

        // 重新计算校验和
        short checksum = CheckSum.computeChkSum(state.packet);
        state.packet.getTcpH().setTh_sum(checksum);

        udt_send(state.packet);

        // 重启该包的定时器
        startTimerForPacket(seqNum);
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

        // 标记该包为已确认
        if (sentPackets.containsKey(ackNum)) {
            PacketState state = sentPackets.get(ackNum);
            if (!state.acked) {
                state.acked = true;
                stopTimerForPacket(ackNum);
                ackedPackets.add(ackNum);
                System.out.println("Marked packet seq=" + ackNum + " as ACKed");
            }
        }

        // SR协议：滑动窗口的条件是baseSeq已被确认
        // 找到最小的未确认的包作为新的baseSeq
        while (ackedPackets.contains(baseSeq)) {
            // 从Map中移除已确认且已滑过的包
            sentPackets.remove(baseSeq);
            ackedPackets.remove(baseSeq);
            System.out.println("Sliding window: removed packet seq=" + baseSeq);
            baseSeq++;
        }

        System.out.println("New baseSeq: " + baseSeq);
        System.out.println("Window after ACK: " + getWindowContents());
    }

    // 辅助方法：计算窗口中的包数量
    private int getWindowSize() {
        int count = 0;
        for (int seq = baseSeq; seq < nextSeqNum; seq++) {
            if (sentPackets.containsKey(seq) && !ackedPackets.contains(seq)) {
                count++;
            }
        }
        return count;
    }

    private String getWindowContents() {
        StringBuilder sb = new StringBuilder("[");
        for (int seq = baseSeq; seq < nextSeqNum; seq++) {
            if (seq > baseSeq) sb.append(" ");
            sb.append(seq);
            if (ackedPackets.contains(seq)) {
                sb.append("(A)");  // A表示已确认
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void waitACK() {
        // 空实现
    }
}