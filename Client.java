
import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.Scanner;

public class Client {
	
	static String config;
	static Properties configProp; 
	static Scanner sc;
	static boolean filepass = false;
	
	public static void main(String[] args) {
		System.out.println("Enter name of configuration file.");
		configProp = new Properties();
		Scanner sc = new Scanner(System.in);
		String config = sc.nextLine();
		System.out.println("Reading config file...");

		try {
			configProp.load(new FileInputStream(config));
			while(filepass!=true)
				checkConfig(configProp);
			configProp.list(System.out);
			System.out.print("\nTrying to connect to server...");	
			
			Socket socket = new Socket(configProp.getProperty("ServerHostname"),
			Integer.parseInt(configProp.getProperty("ServerPort")));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader((new InputStreamReader(socket.getInputStream())));
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			
			ServerListener serverListener = new ServerListener(socket);
			serverListener.start();
			
			String serverInput = null;

			while ((serverInput = stdIn.readLine()) != null) {
				out.println(serverInput);
			}

		}catch(ConnectException e) {
			System.out.println("Unable to connec to server localhost on port "+configProp.getProperty("ServerPort"));
		}catch(IOException e) {
			System.out.println("Configuration file " + config + " could not be found.");
		}
	}
	
	public static void checkConfig(Properties configProp) throws FileNotFoundException, IOException {
		int count=0;
		String[] arr = {"ServerHostname","ServerPort"};
		for(int i=0;i<arr.length;i++) {
			if(!configProp.containsKey(arr[i])) {
				System.out.println(arr[i]+" is a required parameter in the configuration file");
				count++;
			}
		}
		if(count!=0) {
			System.out.println("Enter a correct configuration file: ");
			sc = new Scanner(System.in);
			config = sc.nextLine();
			configProp.load(new FileInputStream(config));
		}else if(count==0) {
			filepass=true;
		}
	}
}

class ServerListener extends Thread{
	Socket socket;
	ServerListener(Socket socket){
		this.socket = socket;
	}
	
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String serverInput;
			while((serverInput = in.readLine())!=null) {
				System.out.println(serverInput);
			}
		}catch(IOException io) {
			
		}
	}
}
