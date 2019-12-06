
import java.util.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.sql.*;
import java.time.LocalTime;

public class Server {

	// read Config.txt file and storing into Property Class
	private Properties configProp = new Properties();
	ArrayList<Client> clist = new ArrayList<Client>();
	LocalTime localTime;
	ServerSocket serverSocket = null;
	Scanner sc = null;
	String config = null;
	HashMap<String, GameRoom> hm = new HashMap<String, GameRoom>();
	Client client = null;
	String parseDB=null;
	String parseRoot=null;
	String parsePass=null;
	String connection=null;
	boolean filepass=false;

	public Server() {
		// to do --> implement your constructor
		
		try {
			System.out.println("Enter name of configuration file.");
			sc = new Scanner(System.in);
			config = sc.nextLine();
			configProp.load(new FileInputStream(config));
			while(filepass!=true)
				checkConfig(configProp);
			configProp.list(System.out);
			
			serverSocket = new ServerSocket(Integer.parseInt(configProp.getProperty("ServerPort")));

			while (true) {
				Socket s = serverSocket.accept(); // blocking
				client = new Client(s);
				clist.add(client);
				client.start();
			}
		} catch (IOException ioe) {
			System.out.println("Configuration file " + config + " could not be found.");
		}
	}
	
	public void checkConfig(Properties configProp) throws FileNotFoundException, IOException {
		int count=0;
		String[] arr = {"ServerHostname","ServerPort","DBConnection","DBUsername","DBPassword","SecretWordFile"};
		for(int i=0;i<arr.length;i++) {
			if(!configProp.containsKey(arr[i])) {
				System.out.println(arr[i]+" is a required parameter in the configuration file");
				count++;
			}
		}
		if(count!=0) {
			System.out.println("Enter a correct configuration file:");
			config = sc.nextLine();
			configProp.load(new FileInputStream(config));
		}else if(count==0) {
			filepass=true;
		}
	}

	public void printTime(String input) {
		System.out.println(LocalTime.now()+". "+input);
	}
	
	public void printTime2(String input) {
		System.out.print(LocalTime.now()+". "+input);
	}

	class Client extends Thread {
		String inputLine, username, password, newEntryString, result, startOrJoin, gameName;
		int wins, losses;
		Socket socket;
		BufferedReader in;
		PrintWriter out;
		Connection conn;
		PreparedStatement ps;
		ResultSet rs;
		boolean turn=false;
		boolean isOut = false;

		Client(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				// Reading in user and password
				readUserandPass();
				// Creating Account
				createAccount(result, username, password, newEntryString);
				//Starting Game
				startGame();
				
			} catch (IOException io) {

			} catch (SQLIntegrityConstraintViolationException e) {
				System.out.println("This username already taken");
			} catch (SQLException e) {
				out.println("Unable to connect to database "+ connection+" with username "+parseRoot+" and password "+parsePass);
				System.out.println("Unable to connect to database "+ connection+" with username "+parseRoot+" and password "+parsePass);
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Error in username & password.");
			}
		}
		
		public void createAccount(String result, String username, String password, String newEntryString)
				throws IOException, SQLIntegrityConstraintViolationException, SQLException {
			while (result == null) {
				printTime(this.username+" - does not have an account so not successfully logged in");
				out.println("No account exists with those credentials. Would you like to create this as an account?");
				if (in.readLine().toLowerCase().equals("yes")) {
					newEntryString = "insert into Persons values (?,?,0,0)";
					ps = conn.prepareStatement(newEntryString);
					ps.setString(1, username);
					ps.setString(2, password);
					ps.executeUpdate();
					printTime(this.username+" - created an account with password "+ this.password);
					// Print Initial Record Stats
					printInitialStats();
					break;
				} else {
					out.println("Enter another username:");
					username = in.readLine();
					out.println("Password: ");
					password = in.readLine();
					ps = conn.prepareStatement(newEntryString);

					ps.setString(1, username);
					rs = ps.executeQuery();
					result = null;
					while (rs.next()) {
						result = rs.getString("password");
					}
				}
			}
			if(result!=null) {
				while(!password.equals(result)){
					printTime(this.username+" - has an account but not successfully logged in.");
					out.println("Wrong password. Try again");
					out.println("Password: ");
					password = in.readLine();
				}
				this.username=username;
				this.password=password;
				printTime(this.username+" - successfully logged in.");
				printInitialStats();
			}
		}

		public void readUserandPass()
				throws IOException, SQLIntegrityConstraintViolationException, SQLException, ClassNotFoundException {
			out.println("Connected!\n");
			
			
			out.println("Username: ");
			username = in.readLine();
			out.println("Password: ");
			password = in.readLine();
			printTime(this.username+" - trying to log in with password "+this.password);
			
			// DATABASE CONNECTION
			newEntryString = "select password from Persons where binary username = ?";
			
			Class.forName("com.mysql.jdbc.Driver");
			System.out.println("Connecting to database...");
			parseDB=configProp.getProperty("DBConnection");
			parseRoot=configProp.getProperty("DBUsername");
			parsePass=configProp.getProperty("DBPassword");
			connection = parseDB+"&user="+parseRoot+"&password="+parsePass;
			conn = DriverManager.getConnection(connection);
			System.out.println("Connected!");
			ps = conn.prepareStatement(newEntryString);

			ps.setString(1, username);
			rs = ps.executeQuery();
			while (rs.next()) {
				result = rs.getString("password");
			}

		}

		public void printInitialStats() throws SQLException {
			newEntryString = "select wins, losses from Persons where binary username = ?";
			ps = conn.prepareStatement(newEntryString);

			ps.setString(1, username);
			rs = ps.executeQuery();

			while (rs.next()) {
				wins = rs.getInt("wins");
				losses = rs.getInt("losses");
			}

			out.println("Great! You are now logged in as " 
					+ username + "! \n\n" 
					+ username + "'s Record \n-------------\nWins - " + wins
					+ "\nLosses - " + losses);
			printTime(this.username+" - has record "+ wins+" wins and "+losses+" losses.");
		}

		public void startGame() throws IOException {
			out.println("\n	1) Start a Game \n	2) Join a Game \nWould you like to start a game or join a game?");
			startOrJoin = in.readLine();
			
			int temp = 0;

			while(temp>2 || temp ==0) {
				
				try {
					temp = Integer.parseInt(startOrJoin);
					if(temp<=2 && temp>=1)
					{
						break;
					}else {
						out.println("Wrong input. Choose 1 or 2");
						startOrJoin = in.readLine();
					}
				}catch(NumberFormatException ie) {
					out.println("Invalid input. Must choose 1 or 2");
					startOrJoin = in.readLine();
				}
			}
			
			out.println("What is the name of the game?");
			gameName = in.readLine();
			int numOfPlayers=0;
			turn=true;
			if(temp==1)
			{
				printTime(this.username+" - wants to start a game called "+gameName);
				while(hm.containsKey(gameName)) {
					printTime(this.username+" - "+ gameName +" already exists, so unable to start  "+gameName);
					out.println(gameName+" already exists. \nEnter another game name:");
					gameName = in.readLine();
				}
				out.println("How many users will be playing (1-4)?");
				String temp2 = in.readLine();
				
				while(numOfPlayers>4 || numOfPlayers==0) {
					try {
						numOfPlayers = Integer.parseInt(temp2);
						if(numOfPlayers<=4 && numOfPlayers>=1) {
							break;
						}
						else {
							out.println("Invalid input. Must choose 1 to 4");
							temp2=in.readLine();
						}
					}catch(NumberFormatException ie) {
						out.println("Invalid input. Must choose 1 to 4");
						temp2=in.readLine();
					}
				}
				createGame(gameName, numOfPlayers, this);
			}else if(temp==2) {
				joinGame(gameName, this);
			}
			
		}
		
		public void createGame(String gameName, int numOfPlayers, Client client) {
			ArrayList<Client> temp = new ArrayList<Client>();
			temp.add(this);
			GameRoom gr = new GameRoom(gameName, numOfPlayers, temp);
			printTime(this.username+" - successfully started game "+gameName);
			printTime(this.username+" - "+gameName+" needs "+ (numOfPlayers) +" to start game");
			hm.put(gameName,  gr);
			gr.start();
		}
		
		public void joinGame(String gameName, Client client) throws IOException {
			printTime(this.username+" - wants to join a game called "+gameName);
			GameRoom temp = hm.get(gameName);
			while(!hm.containsKey(gameName)) {
				printTime(this.username+" - "+gameName+" does not exist so unable to join. Choosing another room");
				out.println(gameName+" does not exist. Choose another game to join.");
				gameName = client.in.readLine();
				temp = hm.get(gameName);
			}
			ArrayList<Client> tempSocket = temp.al;
			while(tempSocket.size() >= temp.numOfPlayers) {
				printTime(this.username+" - "+gameName+" exists, but "+this.username+" unable to join because maximum number of players have already joined "+gameName);
				out.println("The game "+temp.gameName +" does not have space for another user to join. Choose another game to join.");
				gameName = this.in.readLine();
				while(!hm.containsKey(gameName)) {
					out.println(gameName+" does not exist. Choose another game to join.");
					gameName = this.in.readLine();
					temp = hm.get(gameName);
				}
			}
			tempSocket.add(this);
			temp.al = tempSocket;
			for(Client s : temp.al) {
				s.out.println("User "+ this.username+" Joined Game"
						+ "! \n\n" + this.username + "'s Record \n-------------\nWins - " 
						+ this.wins + "\nLosses - " + this.losses);
			}
			printTime(this.username+" - successfully joined "+gameName);
			
			printTime(this.username+" - "+gameName+" needs "+ (temp.numOfPlayers-temp.al.size()) +" to start game");
			if(temp.numOfPlayers-temp.al.size()==0) {
				printTime2(this.username+" - "+temp.gameName+" has "+temp.numOfPlayers+" so starting game. ");
			}
			temp.waiting();
		}
		
	}
	
	class GameRoom extends Thread{
		final int numOfPlayers;
		String gameName, secretWord, displaySecretWord="";
		ArrayList<Client> al = new ArrayList<Client>();
		ArrayList<Client> alcopy = new ArrayList<Client>();
		List<String> allLines;
		int incorrectGuesses=7;
		char guess;
		int playerTurn;
		Connection conn;
		PreparedStatement ps;
		ResultSet rs;
		
		public GameRoom(String gameName, int numOfPlayers, ArrayList<Client> al) {
			this.gameName = gameName;
			this.numOfPlayers = numOfPlayers;
			this.al = al;
		}
		
		public void run() {
			waiting();

			
		}
		
		public void guessing() throws NumberFormatException, IOException {
			boolean game = true;
			while(game) {
				
				for(Client a : al) {
					if(a.turn==true && a.isOut==false) {
						printWord(a);
						othersWait(a);
						int temp=0;
						
						try {
							temp = Integer.parseInt(a.in.readLine());
						}catch(NumberFormatException ie) {
							a.out.println("Invalid input. Must choose 1 or 2");
						}
						
						while(temp>2 || temp==0)
						{
							a.out.println("That is not a valid option.");
							a.out.println("  1) Guess a letter. \n  2) Guess the word. \n\nWhat would you like to do?");
							try {
								temp = Integer.parseInt(a.in.readLine());
							}catch(NumberFormatException ie) {
								a.out.println("Invalid input. Must choose 1 or 2");
							}
						}
						if(temp==1) {
							a.out.println("Letter to guess - ");
							guess=a.in.readLine().charAt(0);
							printTime(gameName+" "+a.username+ " - guessed letter "+ guess);
							checkLogic(a, guess);
							if(!displaySecretWord.contains("_")) {
								printTime(gameName + " "+a.username+" - "+secretWord+" is correct.\n"+a.username+" wins game. ");
								winCondition(a);
								loseConditionLastLetter(a);
								if(al.size()>1) {
									System.out.println("have lost the game");
								}
								game=false;
								break;
							}
							if(incorrectGuesses==0) {
								printTime(gameName+" - ran out of guesses. Everyone lost.");
								loseConditionGuesses();
								game=false;
								break;
							}
							nextPlayer(a);
							
						}else if(temp==2) {
							a.out.println("What is the secret word? ");
							String guessWord = a.in.readLine();
							printTime(gameName+ " "+ a.username+" - guessed word "+ guessWord);
							for(Client c : al) {
								if(!c.equals(a)) {
									if(c.isOut!=true)
										c.out.println(a.username+" has guessed the word '" + guessWord+ "'.");
								}
							}
							if((guessWord.toUpperCase()).equals(secretWord)) {
								printTime(gameName + " "+a.username+" - "+guessWord+" is correct.\n"+a.username+" wins game. ");
								winCondition(a);
								loseConditionNum2(a, guessWord);
								if(al.size()>1) {
									System.out.println("have lost the game");
								}
								game=false;
								break;
							}else {
								printTime(gameName + " "+a.username+" - "+guessWord+" is incorrect.\n"+a.username+" has lost and is no longer in the game.");
								loseCondition(a);
								a.isOut = true;
								nextPlayer(a);
							}
						}	
					}else if(a.turn==true && a.isOut==true) {
						nextPlayer(a);
					}
				}
			}
		}
		
		public void othersWait(Client c) {
			for(Client ax : al) {
				if(!ax.equals(c)) {
					if(ax.isOut!=true)
						ax.out.println("\nWaiting for "+c.username+" to do something...\n");
				}
			}
		}
		
		public void finalRecord(Client a) {

			a.out.println(a.username + "'s Record \n-------------\nWins - " + a.wins
					+ "\nLosses - " + a.losses+"\n");			
			
			for(Client c : alcopy) {
				if(!c.equals(a)) {
					a.out.println(c.username + "'s Record \n-------------\nWins - " + c.wins
							+ "\nLosses - " + c.losses+"\n");
				}
				
			}
			
			a.out.println("\n\nThank you for playing Hangman!");
		}
		
		public void loseUpdate(Client a) {
			String newEntryString = "update Persons SET losses = ? where username = ?";
			a.losses++;
			
			try {
				Class.forName("com.mysql.jdbc.Driver");
				
				conn = DriverManager.getConnection(connection);
				
				ps = conn.prepareStatement(newEntryString);
				
				ps.setInt(1, a.losses);
				ps.setString(2, a.username);
				ps.executeUpdate();
			} catch (ClassNotFoundException | SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		public void loseConditionGuesses() {
			for(Client a : al) {
					if(a.isOut!=true) {
						loseUpdate(a);
						a.out.println("Ran out of guesses. Game over.");
						a.out.println("You lose!\nThe word was "+"\""+secretWord+"\".\n");
					}
			}
			for(Client a: al) {
					if(a.isOut!=true) {
						finalRecord(a);
				}
			}
		}
		
		public void loseConditionLastLetter(Client winner) {
			for(Client a : al) {
				if(!a.equals(winner)) {
					if(a.isOut!=true) {
						loseUpdate(a);
						a.out.println(winner.username+" has guessed correctly. Game over.");
						a.out.println("You lose!\nThe word was "+"\""+secretWord+"\".\n");
						System.out.print(a.username+", ");
					}
				}
			}	
			for(Client a: al) {
				if(!a.equals(winner)) {
					if(a.isOut!=true) {
						finalRecord(a);
					}
				}
			}
		}
		
		public void loseCondition(Client a) {
			a.out.println("That is incorrect! You lose!\nThe word was "+"\""+secretWord+"\".");
			loseUpdate(a);
			for(Client c : al) {
				if(!c.equals(a)){
					if(c.isOut!=true) {
						c.out.println(" "+a.username+" guess the word incorrectly. "+a.username+" is out of the game.\n");
					}
						
				}
			}
			finalRecord(a);
		}
		
		public void loseConditionNum2(Client winner, String guess) {
			for(Client a : al) {
				if(!a.equals(winner)) {
					if(a.isOut!=true) {
						loseUpdate(a);
						a.out.println(winner.username+ " guessed the word correctly. You lose!\n");
						System.out.print(a.username+", ");
					}
				}
			}
			for(Client a: al) {
					if(a.isOut!=true) {
						finalRecord(a);
				}
			}
				
		}
		
		public void winCondition(Client a) {
			try {
				String newEntryString = "update Persons SET wins = ? where username = ?";
				a.wins++;
				
				Class.forName("com.mysql.jdbc.Driver");
				conn = DriverManager.getConnection(connection);
				
				ps = conn.prepareStatement(newEntryString);
				
				ps.setInt(1, a.wins);
				ps.setString(2, a.username);
				ps.executeUpdate();
				
				a.out.println("That is correct! You win!\n");
								
			} catch (SQLException | ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
		
		public void nextPlayer(Client a) {
			
			int maxSize = al.size();
			for(int i=0; i<al.size();i++) {
				if(al.get(i).turn==true) {
					playerTurn=i;
					break;
				}
			}
			playerTurn = (playerTurn+1)%maxSize;
			a.turn=false;
			al.get(playerTurn).turn=true;
			System.out.println(playerTurn);
		}
		
		public void checkLogic(Client a, char guess) {
			if(secretWord.contains(String.valueOf(guess).toUpperCase())) {
				for(Client c : al) {
					if(c.isOut!=true) {
						if(!c.equals(a)) {
							c.out.println(a.username + " has guessed letter "+guess+".\n");
						}
						c.out.println("The letter '"+guess+"' is in the secret word\n");
					}
					
				}
				System.out.print(a.gameName + " "+a.username+" - "+guess+" is in "+ secretWord+" in position(s) ");
				for(int i =0; i<secretWord.length();i++) {
					if(secretWord.charAt(i)==Character.toUpperCase(guess)) {
						char[] tempChar = displaySecretWord.toCharArray();
						tempChar[i*2]=Character.toUpperCase(guess);
						displaySecretWord = String.valueOf(tempChar);
						System.out.print(i+" ");
					}
				}
				String dp = displaySecretWord;
				System.out.print(". Secret word now shows "+dp.replaceAll("_", ""));
			}else {
				incorrectGuesses--;
				printTime(a.gameName + " "+a.username+" - "+ guess+" is not in "+secretWord+". "+a.gameName+" now has "+incorrectGuesses+" guesses remaining");
					for(Client c : al) {
						if(c.isOut!=true) {
						if(!c.equals(a)) {
							c.out.println("\n"+a.username + " has guessed letter "+guess);
						}
						c.out.println("\nThe letter '"+guess+"' is not in the secret word\n");
					}
				}
						
				}
		}
		
		public void printWord(Client a) {
			for(Client c : al) {
				if(c.isOut!=true) {
					c.out.print("Secret Word ");
					c.out.println(displaySecretWord);
					c.out.print("\nYou have " +incorrectGuesses+ " incorrect guesses remaining.");
				}
				
			}
			a.out.println("\n  1) Guess a letter. \n  2) Guess the word. \n\nWhat would you like to do?");
				
		}
		
		public void secretWord() {
			
			String config = configProp.getProperty("SecretWordFile");
			try {
				allLines = Files.readAllLines(Paths.get(config));
			} catch (IOException e) {
				e.printStackTrace();
			}
					
			Random rand = new Random();
			secretWord = allLines.get(rand.nextInt(allLines.size())).toUpperCase();
			
			for(Client a : al) {
				a.out.println("\nDeterming secret word...\n");
			}
			
			for(int i=0; i<secretWord.length();i++) {
				displaySecretWord+="_ ";
			}			
			
			System.out.println("Secret word is "+secretWord);
		}
		
		public void waiting() {
			if((numOfPlayers-al.size())!=0) {
				for(Client c : al) {
					c.out.println("\n"+al.size()+" waiting for "+ (numOfPlayers-al.size()) +" other user to join...\n");
				}
			}else {
				alcopy = al;
				for(Client c : al) {
					c.out.println("\nAll user have joined.");
				}
				secretWord();
				try {
					guessing();
				} catch (NumberFormatException | IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main(String[] args) {
		Server svr = new Server();
	}
}
