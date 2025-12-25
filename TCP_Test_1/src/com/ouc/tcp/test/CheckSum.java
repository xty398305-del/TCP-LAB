package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;
import com.ouc.tcp.message.TCP_SEGMENT;

public class CheckSum {
	
	/*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
        TCP_HEADER tcpH = tcpPack.getTcpH();
        TCP_SEGMENT tcpS = tcpPack.getTcpS();

        // 获取需要校验的字段
        int seq = tcpH.getTh_seq();
        int ack = tcpH.getTh_ack();
        int[] data = tcpS.getData();

        // 初始化校验和为0
        long sum = 0;

        // 1. 校验seq字段（32位，分成两个16位处理）
        sum += (seq >> 16) & 0xFFFF;  // 高16位
        sum += seq & 0xFFFF;         // 低16位

        // 2. 校验ack字段（32位，分成两个16位处理）
        sum += (ack >> 16) & 0xFFFF;  // 高16位
        sum += ack & 0xFFFF;         // 低16位

        // 3. 校验和字段本身
        // sum += 0;  // 校验和字段计算时设为0

        // 4. 校验数据字段（每个int是32位，需要拆分成两个16位）
        for (int i = 0; i < data.length; i++) {
            int value = data[i];
            sum += (value >> 16) & 0xFFFF;  // 高16位
            sum += value & 0xFFFF;         // 低16位
        }

        // 处理进位（回卷）
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        // 取反码
        short checksum = (short) ~sum;
        return checksum;
	}
	
}
