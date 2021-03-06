package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import server.sender.MassageSender;
import server.states.BaseState;
import server.states.PreFlopState;
import server.view.TableView;
import util.FinalCard;

public class Game {
//	public static String[] PLAYER_ACTIONS = {"check", "bet", "call", "raise", "all-in", "fold"};
	
	public static class PlayerActions {
		public static String CHECK = "call";	//0
		public static String BET = "fold";	//1
		public static String CALL = "check";	//2
		public static String RAISE = "raise";	//3
		public static String ALL_IN = "all-in";	//4
		public static String FOLD = "bet";	//5
	}
	
	public static final String[] actionIndex = {
		"call", "fold", "check", "raise", "all-in", "bet"
	};
	
	private int pot;
	private Table table;
	
	private BaseState state;
	private List<Player> playerList;
	
	private Deck deck;
	private List<Card> boardCards;
	
	private boolean isEnd;
	
	public TableView tableView;
	
	public List<Card> getBoardCards() {
		return boardCards;
	}

	public void setBoardCards(List<Card> boardCards) {
		this.boardCards = boardCards;
	}
	
	public Game(Table table) {
		this.pot = 0;
		this.table = table;
		this.state = (BaseState)new PreFlopState(this);
		this.playerList = table.getPlayerList();
		this.deck = Deck.getDeckInstance();
		this.boardCards = new ArrayList<Card>(7);
		this.isEnd = false;
	}
	
	public int getPot() {
		return pot;
	}

	public void setPot(int pot) {
		this.pot = pot;
	}

	public Table getTable() {
		return table;
	}

	public void setTable(Table table) {
		this.table = table;
	}

	public BaseState getState() {
		return state;
	}

	public void setState(BaseState state) {
		this.state = state;
	}

	public void setPlayerList(List<Player> playerList) {
		this.playerList = playerList;
	}

	public boolean isEnd() {
		return isEnd;
	}

	public void setEnd(boolean isEnd) {
		this.isEnd = isEnd;
	}

	public void init() {
		for(Player player : playerList) {
			if(0 == player.getChip()){
				player.setState(Player.States.LOSE);
			}
			player.setActionChip(0);
		}
		
		this.pot = 0;
		this.deck = Deck.getDeckInstance();
		this.boardCards.clear();

		for(Player player : playerList){
			player.getCards().clear();
			player.setTotalIn(0);
		}
	}
	
	public void initPlayersState(){
		for(Player player : playerList) {
			if(!player.getState().equals(Player.States.FOLD)
					&& !player.getState().equals(Player.States.ALL_IN)
					&& !player.getState().equals(Player.States.LOSE)){
				player.setState(Player.States.WAIT);
				player.setActionChip(0);
			}
		}
	}
	
	public void initBlinds(){
		for(Player player : playerList) {
			if(!player.getState().equals(Player.States.LOSE)){
				player.setState(Player.States.WAIT);
			}
		}
	}
	
	public void increaseSmBlindIndex(){
//		table.smBlindIndex = (table.smBlindIndex + 1) % playerList.size();
		table.smBlindIndex = nextIndex(table.smBlindIndex);
	}
	
	public void start() {
		
		tableView = new TableView(table.getPlayerNumber());
		tableView.show();
		
		while(!isEnd){
//			init();
			state.action();
		}
	}
	
	void initChip(){
		prepareInitMsg();
		MassageSender.init(getPlayerList(), Table.inChip);
	}
	
	/**
	 * Prepare initial messages in form of:
	 * init allchip smblind bigblind ante pid0,pi1,pi2... self_pid
	 * Stored in Player object.
	 * @return
	 */
	private void prepareInitMsg() {
		StringBuffer pids = new StringBuffer();
		
		List<Player> players = getPlayerList();
		
		for (Player p : players) {
			pids.append(p.getPid());
			pids.append(",");
		}
		
		//Remove the last ','
		pids.deleteCharAt(pids.length() - 1);
		String initMsgStub = "init " + Table.inChip + " " + Table.smBlind + " " +
				Table.bBlind + " " + Table.ante + " " + pids + " ";
		
		for (Player p : players) {
			p.setInitMsg(initMsgStub + p.getPid());
		}
	}
	
	public boolean isOnlyOneAlive() {
		int playerAlive = 0;
		for(Player player : playerList) {
			if(!player.getState().equals(Player.States.FOLD) && !player.getState().equals(Player.States.LOSE)){
				playerAlive++;
				if(playerAlive > 1){
					return false;
				}
			}
		}
		return true;
	}
	
	public Player getAlive(){
		for(Player player : playerList) {
			if(!player.getState().equals(Player.States.FOLD) && !player.getState().equals(Player.States.LOSE)){
				return player;
			}
		}
		return null;
	}
	
	public boolean isAllCall() {
		
		for(Player player : playerList) {
			if((player.getState().equals(Player.States.WAIT))
					|| (player.getActionChip() != state.getBetChip() && !player.getState().equals(Player.States.FOLD) && !player.getState().equals(Player.States.ALL_IN) &&  !player.getState().equals(Player.States.LOSE))){
				return false;
			}
		}
		
		return true;
	}
	
	public List<Player> getPlayerList() {
		return this.playerList;
	}
	
	public int getSmBlind() {
		return table.smBlindIndex;
	}
	
	public String getUpdateMassage(Player player) {
		StringBuilder builder = new StringBuilder();
		builder.append("uv ");
		builder.append(table.getPlayerNumber() + " ");
		builder.append(pot+ " ");
		builder.append(state);
		//add boardcards
		if(0 != boardCards.size()){
			builder.append(" " + boardCards.get(0));
			for(int i = 1; i < boardCards.size(); i++) {
				builder.append("," + boardCards.get(i));
			}
		}
		//add player info
		int index = playerList.indexOf(player);
		for(int i = 1; i <= playerList.size(); i++){
			builder.append(" " + playerList.get((index + i) % playerList.size()).getUpdateMassage());
		}
		return builder.toString();
	}
	
	public void processResult() {
		collectFinalCard();
		
		//Init lose counter for each player.
		for ( int i = 0; i < playerList.size(); i++ ) {
			for ( int j = i + 1; j < playerList.size(); j++ ) {
				Player p1 = playerList.get(i);
				Player p2 = playerList.get(j);
				int cmpResult = p1.getFinalCard().compareTo(p2.getFinalCard());
				if ( cmpResult < 0 ) {
					p1.loseCounter++;
				}
				else if ( cmpResult > 0 ) {
					p2.loseCounter++;
				}
			}
		}
		
		int rank = 0;
		while (pot > 0) {
			ArrayList<Player> winPlayer = new ArrayList<Player>();
			
			//Get all win players.
			for (Player p : playerList) {
				if (p.loseCounter == rank) {
					winPlayer.add(p);
				}
			}
			
			if (winPlayer.isEmpty()) {
				break;
			}
			
			//Sort win player ascendant by their total in.
			Comparator<Player> comparator = new PlayerTotalInComparator();
			Collections.sort(winPlayer, comparator);
			
			//Calculate one by one from the shortest.
			for ( int i = 0; i < winPlayer.size(); i++ ) {
				Player winner = winPlayer.get(i);
				//Collect chips.
				int winnerTotalIn = winner.getTotalIn();
				int winSum = 0;
				
				for (Player p : playerList) {
					if (p.getTotalIn() <= 0) {
						continue;
					}
					if (winPlayer.contains(p)) {
						p.setTotalIn(p.getTotalIn() - winnerTotalIn);
						winSum += winnerTotalIn;
						continue;
					}

					int actualWin = p.getTotalIn() > winnerTotalIn ? 
							winnerTotalIn : p.getTotalIn();
					winSum += actualWin;
					p.setTotalIn(p.getTotalIn() - actualWin);
				}
				
				pot -= winSum;
				//Allocate win sum averagely to all winners.
				int alocSize = winPlayer.size() - i;
				int avgWin = winSum / alocSize;
				int odds = winSum % alocSize;
				
				boolean oddsAllocated = false;
				for (int j = i; j < winPlayer.size(); j++) {
					Player p = winPlayer.get(j);
					p.setChip(p.getChip() + avgWin);
					
					if (! oddsAllocated) {
						p.setChip(p.getChip() + odds);
						oddsAllocated = true;
					}
				}
				
				
			}
			rank++;
		}
		
		//Giva back rest chips.
		for (Player p : playerList) {
			p.setChip(p.getChip() + p.getTotalIn());
			p.setTotalIn(0);
		}
	}
	
	public boolean isThereAWiner() {
		
		int aliveCount = 0;
		for(Player player : playerList) {
			if(0 != player.getChip()){
				aliveCount++;
				if(aliveCount > 1){
					return false;
				}
			}
		}
		return true;
	}
	
	public void endTable(){
		setEnd(true);
	}
	
	public Card deal2player(Player player) {
		Card card = deck.deal();
		player.getCards().add(card);
		return card;
	}
	
	public Card deal2Board() {
		Card card = deck.deal();
		boardCards.add(card);
		return card;
	}
	
	/**
	 * return action part of "your turn" message
	 * @param playerIndex
	 * @return
	 */
	public String getPlayerAvalableAction(int playerIndex) {
		Player currentPlayer = playerList.get(playerIndex);

		//player's state is fold or all-in or lose
		if(currentPlayer.getState().equals(Player.States.ALL_IN)
				|| currentPlayer.getState().equals(Player.States.FOLD)
				|| currentPlayer.getState().equals(Player.States.LOSE)){
			return null;
		}
		
		//preflop && bb
		if(state.getState().equals("preflop")
				&& playerIndex == nextIndex(table.smBlindIndex)) {
			if(getState().getBetChip() == Table.bBlind){
					return "0 1 1 1 1 0";
			}
		}
		
		//player's state is wait and no one bet
		if(currentPlayer.getState().equals(Player.States.WAIT)
				&& state.getBetChip() == 0){
			return "0 1 1 0 1 1";
		} 
		
		return determinByChip(currentPlayer);
	}
	
	private String determinByChip(Player currentPlayer) {
		if(currentPlayer.getChip() > state.getBetChip()-currentPlayer.getActionChip()){
			return "1 1 0 1 1 0";
		} else {
			return "0 1 0 0 1 0";
		}
	}
	
	private int nextIndex(int currentIndex){
		return (currentIndex + 1) % playerList.size();
	}
	
	
	/*
	 *	player's action 
	 */
	public void bet(Player player, int chip){
		// TODO Auto-generated method stub
		pot = pot - player.getActionChip();
		int realChip = player.bet(chip);
		pot = pot + realChip;
		if(realChip > state.getBetChip()){
			state.setBetChip(realChip);
		}
	}
	
	public void raise(Player player, int chip){
		// TODO Auto-generated method stub
		pot = pot - player.getActionChip();
		int realChip = player.raise(chip);
		pot = pot + realChip;
		if(realChip > state.getBetChip()){
			state.setBetChip(realChip);
		}
	}
	
	public void call(Player player){
		// TODO Auto-generated method stub
		pot = pot - player.getActionChip();
		int realChip = player.call(state.getBetChip());
		pot = pot + realChip;
		if(realChip > state.getBetChip()){
			state.setBetChip(realChip);
		}
	}
	
	public void check(Player player){
		// TODO Auto-generated method stub
		player.check();
	}
	
	public void allIn(Player player){
		// TODO Auto-generated method stub
		pot = pot - player.getActionChip();
		int realChip = player.allIn();
		pot = pot + realChip;
		if(realChip > state.getBetChip()){
			state.setBetChip(realChip);
		}
	}
	
	public void fold(Player player){
		// TODO Auto-generated method stub
		player.fold();
	}
	
	public void printGameState() {
		System.out.println(state);
		System.out.println("Pot: " + pot);
		System.out.println("BetChip: " + state.getBetChip());
	}
	
	public void getGameState() {
		StringBuffer sb = new StringBuffer();
		sb.append(state);
		sb.append("|");
		System.out.println(state);
		System.out.println("Pot: " + pot);
		System.out.println("BetChip: " + state.getBetChip());
	}

	/**
	 * Set all player's final card for calculating result.
	 * @return
	 */
	private ArrayList<FinalCard> collectFinalCard() {
		for ( int i = 0; i < getPlayerList().size(); i++ ) {
			Player tmp = getPlayerList().get(i);
			tmp.setFinalCard(getPlayerFinalCardType(getBoardCards(), tmp.getCards()));
		}
		return null;
	}
	/**
	 * Get a player's final card by his pocket card and board card.
	 * @param boardCard
	 * @param pocketCard
	 * @return 
	 */
	private FinalCard getPlayerFinalCardType(List<Card> boardCard, List<Card> pocketCard) {
		assert(boardCard.size() == util.Util.COMMUNITY_CARD_NUM && pocketCard.size() == util.Util.POCKET_CARD_NUM);
		
		ArrayList<Card> avaCard = new ArrayList<Card>();
		
		for ( int i = 0; i < boardCard.size(); i++ ) {
			avaCard.add(boardCard.get(i));
		}

		for ( int i = 0; i < pocketCard.size(); i++ ) {
			avaCard.add(pocketCard.get(i));
		}
		
		return util.Util.deterEffectCard(avaCard);
	}
	
	/**
	 * test case
	 * @param arg
	 */
	public static void main(String[] arg) {
		Player p1 = new Player(0, null);
		Player p2 = new Player(0, null);
		Player p3 = new Player(0, null);
		
		ArrayList<Card> c1 = new ArrayList<Card>();
		ArrayList<Card> c2 = new ArrayList<Card>();
		ArrayList<Card> c3 = new ArrayList<Card>();
		
		c1.add(new Card("S", 14));
		c1.add(new Card("H", 14));

		c2.add(new Card("D", 14));
		c2.add(new Card("C", 14));

		c3.add(new Card("S", 12));
		c3.add(new Card("H", 12));

		p1.setTotalIn(50);
		p2.setTotalIn(100);
		p3.setTotalIn(150);
		
		p1.setCards(c1);
		p2.setCards(c2);
		p3.setCards(c3);
		
		Table table = Table.getTableInstance();
		table.addPlayer(p1);
		table.addPlayer(p2);
		table.addPlayer(p3);
		
		ArrayList<Card> board = new ArrayList<Card>();
		board.add(new Card("S", 5));
		board.add(new Card("S", 4));
		board.add(new Card("C", 6));
		board.add(new Card("D", 7));
		board.add(new Card("H", 8));
		
		Game game = new Game(table);
		game.setBoardCards(board);
		game.setPot(300);
		
		game.print_snap_shot();
		
		game.processResult();
		
		game.print_snap_shot();
	}
	
	private void print_snap_shot() {
		System.out.println("Snap Shot ===================== ");
		for (Player p : playerList) {
			System.out.println(p.getUpdateMassage());
		}
	}
}
