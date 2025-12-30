package org.server;
import java.net.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
public class TCPClient extends JFrame {	
	private JLabel stateLB;
	private JTextArea centerTextArea , inputTextArea;
	private JPanel southPanel,bottompanel;
	private JTextField ipTextField,remotePortTF;
	private JButton sendBT,clearBT;
	static Socket server;
	String name=null ;
	PrintWriter out ;
	BufferedReader in ;
	Thread receive=new Thread(new receiveThread());
	public TCPClient() throws Exception{
		server = new Socket(InetAddress.getLocalHost(), 5678);
		 in = new BufferedReader(new InputStreamReader(server.getInputStream()));
		 out = new PrintWriter(server.getOutputStream());
		receive.start();
		 
	}	
	public void setUpUI(){
		setTitle("客户端");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(400, 400);
		setResizable(false);  //窗口大小不可调整
		setLocationRelativeTo(null); //窗口剧中
		stateLB =new JLabel("未链接");
		stateLB.setHorizontalAlignment(JLabel.RIGHT);
		//窗口的center部分
		centerTextArea=new JTextArea();
		centerTextArea.setEditable(false);
		centerTextArea.setBackground(new Color(211, 211, 211));
		//窗口的SHOTU部分
		southPanel =new JPanel(new  BorderLayout());
		inputTextArea=new JTextArea(5,20);
		bottompanel=new JPanel(new FlowLayout(FlowLayout.CENTER,5,5));
		ipTextField=new JTextField("127.0.0.1",8);
		sendBT=new JButton("发送");
		clearBT=new JButton("清屏");
		bottompanel.add(ipTextField);
		bottompanel.add(sendBT);
		bottompanel.add(clearBT);
		southPanel.add(new JScrollPane(inputTextArea),BorderLayout.CENTER);
		southPanel.add(bottompanel,BorderLayout.SOUTH);		
		add(stateLB,BorderLayout.NORTH);
		add(new JScrollPane(centerTextArea),BorderLayout.CENTER);
		add(southPanel,BorderLayout.SOUTH);
		setVisible(true);
		name=JOptionPane.showInputDialog(this,"请输入客户端的名字",JOptionPane.QUESTION_MESSAGE);
		setTitle(name);
		out.println(name);
		out.flush();
		stateLB.setText(name+"已链接");
	}
	public void setListener(){
		sendBT.addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				// TODO Auto-generated method stub	
			String sendcontent=inputTextArea.getText();			
			centerTextArea.append(inputTextArea.getText()+"\n");
			centerTextArea.setCaretPosition(centerTextArea.getText().length());			
			inputTextArea.setText("");			
			try {					
					out.println(sendcontent);
					out.flush();
					if (sendcontent.equals("end")) {
					server.close();
					}					
			} catch (Exception e2) {
				// TODO: handle exception
				JOptionPane.showMessageDialog(TCPClient.this, "出错了发送不成功");
				e2.printStackTrace();
			}			
			clearBT.addActionListener(new ActionListener() {				
				@Override
				public void actionPerformed(ActionEvent e) {
					centerTextArea.setText("");//清空聊天记录
				}
			});			
			}
		});
	}
class receiveThread implements Runnable{
//接收线程
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			while(server!=null)
			{
				String info =in.readLine();
				centerTextArea.append(info+"\n");
				centerTextArea.setCaretPosition(centerTextArea.getText().length());
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	
}	
	public static void main(String[] args) throws Exception {
		TCPClient c=new TCPClient();
		c.setUpUI();
		c.setListener();
	}
}
