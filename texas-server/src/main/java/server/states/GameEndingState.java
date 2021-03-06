package server.states;

import java.util.List;

import server.Game;
import server.Player;
import server.sender.MassageSender;

public class GameEndingState extends BaseState{

	public GameEndingState(Game game) {
		super(game);
		// TODO Auto-generated constructor stub
		this.state = "river";
	}

	@Override
	public void action() {
		// TODO Auto-generated method stub
		if(game.isOnlyOneAlive()){
			Player player = game.getAlive();
			player.setChip(player.getChip() + game.getPot());
		}else{
			this.game.processResult();
		}
		
		game.tableView.updateAllPlayer(game.getPlayerList());
		
		List<Player> playerList = this.game.getPlayerList();
		StringBuffer sb = new StringBuffer();
		for(Player player : playerList) {
			sb.append(player.getUpdateMassage() + " ");
		}
		
		System.out.println(sb.toString());
		
		next();
	}

	@Override
	public void next() {
		// TODO Auto-generated method stub
		if (this.game.isThereAWiner()){
			this.game.endTable();
			MassageSender.sayGoodbye(this.game.getPlayerList());

		} else {
			this.game.init();
			this.game.setState(new PreFlopState(this.game));
		}
	}

	@Override
	public void beforePlayerAction() {
		// TODO Auto-generated method stub
		
	}

}
