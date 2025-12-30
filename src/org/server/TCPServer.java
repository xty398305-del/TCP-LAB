package org.server;
import java.net.*;  
import java.util.ArrayList;
import java.util.List;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.*;  

import javax.annotation.processing.Messager;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
/*
 *  author yang 2015 7 6
 */
public class TCPServer extends JFrame{ 
	public static JTextArea centerTextArea=new JTextArea();
	private JPanel southPanel,bottompanel;
	public List<Client> list =new ArrayList<Client>();
	String name1;
	
	public void setui()throws Exception {
		 ServerSocket ss = new ServerSocket(5678); //创建一个Socket服务器，监听5566端口  	
		// TODO Auto-generated constructor stub
		  setTitle("服务器");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setSize(400, 400);
			setResizable(false);  //窗口大小不可调整
			setLocationRelativeTo(null); //窗口剧中
			//窗口的center部分
			centerTextArea.setEditable(false);
			centerTextArea.setBackground(new Color(211, 211, 211));
			//窗口的SHOTU部分
			southPanel =new JPanel(new  BorderLayout());
			bottompanel=new JPanel(new FlowLayout(FlowLayout.CENTER,5,5));	
			southPanel.add(bottompanel,BorderLayout.SOUTH);		
			add(new JScrollPane(centerTextArea),BorderLayout.CENTER);
			add(southPanel,BorderLayout.SOUTH);
			setVisible(true);
			  while(true){  
			      Socket s = ss.accept();//利用Socket服务器的accept()方法获取客户端Socket对象。  
			    addclient(s);		     	
			      System.out.println(list.size()); 
			    } 
	}	
  //添加客户端
  private void addclient(Socket s){ 
      try {
		BufferedReader   in = new BufferedReader(new InputStreamReader(
					s.getInputStream()));
		name1=in.readLine();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
      Client c = new Client(name1,s); //创建客户端处理线程对象  
      list.add(c);
      Thread t =new Thread(c); //创建客户端处理线程  
      t.start();//启动线程			     	
  }
    
//客户端处理线程类(实现Runnable接口)  
class Client implements Runnable  {  
  String name;//客户端名字
  Socket s = null;//保存客户端Socket对象
  BufferedReader in;
  PrintWriter out;
  Client(String name,Socket s){  
    this.s = s; 
    this.name=name;
    try {
		in = new BufferedReader(new InputStreamReader(
				s.getInputStream()));
		out = new PrintWriter(s.getOutputStream());
		centerTextArea.append("客户端"+name+"连接成功\n");
		centerTextArea.setCaretPosition(centerTextArea.getText().length());				
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}  
  }    
  public void run(){  
		try {
			while (true) {
				String str = in.readLine();	
				for(int j=0;j<list.size();j++){
					Client c = list.get(j);
					if (c!=this) {
								System.out.println(str);
								c.send(str);							
						}					
					}
				centerTextArea.append(name+"发出消息："+str+"\n");
				centerTextArea.setCaretPosition(centerTextArea.getText().length());				
				
			if (str.equals("end"))
					break;
			}
			try {
				s.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	} 
	public void send(String str) {
		out.println("客户端  "+name+" 说："+str);
		out.flush();
	}  
}
public static void main(String[] args) throws Exception{  
    //利用死循环不停的监听端口  
    TCPServer tc=new TCPServer();
    tc.setui(); 
 }
}