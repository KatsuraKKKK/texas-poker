package server.sender;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import server.Card;
import server.Game;
import server.Player;
import server.utils.IOHelper;

public class MassageSender {

	public static void init(PrintWriter pw, int chip) {
		// TODO Auto-generated method stub
		pw.println("init " + chip);
	}
	
	public static void updateView(List<Player> playerList, String massage) {
		for(Player player : playerList) {
			PrintWriter pw = null;
			try {
				pw = IOHelper.getWriter(player.getSocket());
				pw.println(massage);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {

			}
		}
	}
	
	public static void deal(PrintWriter pw, Player player) {
		List<Card> poket = player.getCards();
		StringBuilder builder = new StringBuilder();
		builder.append("deal ");
		builder.append(player.getSocket().getPort() + " ");
		builder.append(poket.get(0)+" ");
		builder.append(poket.get(1));
		pw.println(builder.toString());
	}

}
