//import ADMark.*;
import javafx.scene.*;
import javax.swing.*;
import java.awt.*;
import javafx.scene.web.WebView;
import javafx.embed.swing.*;
import javafx.application.Platform;
import java.awt.event.*; 
import java.lang.System;
import java.io.*;


class webview 
{
	



	public static void main(String args[])
	{	

//		ADMark markdownEngine;
		JFrame obj = new JFrame("markdown-Html Engine");
		JPanel buttonpanel = new JPanel();
		JPanel viewport = new JPanel();
		JTextArea markdown = new JTextArea(400,200);
		JTextArea html = new JTextArea(400,200);
		JFXPanel jfxPanel = new JFXPanel();

		
		JScrollPane scroll = new JScrollPane(viewport);
	
		JButton htmlButton = new JButton("HTML VIEW");
		JButton markdownButton = new JButton("MARKDOWN VIEW");
		JButton webviewButton = new JButton("WEB VIEW");

		buttonpanel.add(htmlButton);
		buttonpanel.add(markdownButton);
		buttonpanel.add(webviewButton);	

		
		viewport.add(html);
		viewport.add(jfxPanel);
		viewport.add(markdown);		

		markdown.setVisible(false);
		html.setVisible(true);
		jfxPanel.setVisible(false);
		
		
	
	
		
		
	
		markdownButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				markdown.setVisible(true);
				html.setVisible(false);
				jfxPanel.setVisible(false);
				System.out.println("markdown view");

			}
		});

		htmlButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){


				String content=""; // Content
				String line;
				try{
					BufferedReader reader = new BufferedReader(new FileReader("./output_html.html"));
					while(true){
						line=reader.readLine();
						if (line==null) {
							break;
						}
						content = String.format("%s%s\n", content, line);
					}

				}catch(Exception error){
					System.out.println(error);
					error.printStackTrace();
				}

				html.setText(content);
				markdown.setVisible(false);
				html.setVisible(true);
				jfxPanel.setVisible(false);
				
				System.out.println("html view");
			}
		});

		webviewButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				markdown.setVisible(false);
				html.setVisible(false);
				jfxPanel.setVisible(true);

				String htmlcontent=html.getText();
				
					Platform.runLater(()->{
				    WebView webView = new WebView();
				  
				    jfxPanel.setScene(new Scene(webView,1800,1100));
				    webView.getEngine().loadContent(htmlcontent,"text/html");
	
				});
	
				System.out.println("webview view");
			}
		});



		obj.add(buttonpanel,BorderLayout.NORTH);
		obj.add(scroll,BorderLayout.CENTER);		
		
		obj.setSize(1100,900);
		obj.setVisible(true);
		obj.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
}

