package isaac.bastion;


import java.util.Random;

import isaac.bastion.util.QTBox;

import com.untamedears.citadel.Citadel;
import com.untamedears.citadel.entity.Faction;
import com.untamedears.citadel.entity.IReinforcement;
import com.untamedears.citadel.entity.PlayerReinforcement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

@SuppressWarnings("rawtypes")
public class BastionBlock implements QTBox, Comparable
{
	private Location location;
	private int id;
	private float balance=0;
	private int strength;
	private int radius;
	private long placed;

	private long lastPlace;
	private int radiusSquared;
	private boolean loaded=true;
	private boolean ghost=false;

	private static int highestId=0;
	private static int min_break_time;
	private static int erosionTime;
	private static boolean first=true;
	private int taskId;
	private static Random random;

	public BastionBlock(Location nlocation, PlayerReinforcement reinforcement){
		id=++highestId;
		location = nlocation;
		radius=Bastion.getConfigManager().getBastionBlockEffectRadius();
		radiusSquared=radius*radius;
		placed=System.currentTimeMillis();

		strength=reinforcement.getDurability();
		loaded=false;

		setup();

	}
	public BastionBlock(Location nLocation,long nPlaced,float nBalance,int nID)
	{
		id=nID;
		location = nLocation;
		radius=Bastion.getConfigManager().getBastionBlockEffectRadius();
		placed=nPlaced;
		balance=nBalance;

		radiusSquared=radius*radius;

		PlayerReinforcement reinforcement = (PlayerReinforcement) Citadel.getReinforcementManager().getReinforcement(location.getBlock());

		strength=reinforcement.getDurability();
		setup();

	}
	private void setup(){
		if(id>highestId){
			highestId=id;
		}
		Bastion.getPlugin().getLogger().info("Bastion Block created");
		if(first){
			intiStatic();
		}
		if(erosionTime!=0){
			taskId=registerTask();
		}
		balance=0;
		lastPlace=System.currentTimeMillis();

	}
	private void intiStatic(){

		if(Bastion.getConfigManager().getBastionBlockMaxBreaks()!=0){
			min_break_time=(1000*60)/Bastion.getConfigManager().getBastionBlockMaxBreaks();
		} else{
			min_break_time=Integer.MAX_VALUE;
		}


		if(Bastion.getConfigManager().getBastionBlockErosion()!=0){
			erosionTime=(1000*60*60*24*20)/Bastion.getConfigManager().getBastionBlockErosion();
		} else{
			erosionTime=0;
		}

		random=new Random();
		first=true;
	}
	private int registerTask(){
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		return scheduler.scheduleSyncRepeatingTask(Bastion.getPlugin(),
				new BukkitRunnable(){
			public void run(){
				erode(1);
			}
		},
		random.nextInt(erosionTime),erosionTime);
	}
	public void close(){
		if(!ghost){
			if(erosionTime!=0){
				BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
				scheduler.cancelTask(taskId);
			}
			loaded=false;
			ghost=true;
			location.getBlock().setType(Material.AIR);
		}
	}
	private float erosionFromPlace(){
		int scaleTime=Bastion.getConfigManager().getBastionBlockScaleTime();
		double scaleStart=Bastion.getConfigManager().getBastionBlockScaleFacStart();
		double scaleEnd=Bastion.getConfigManager().getBastionBlockScaleFacEnd();
		int time=(int) (System.currentTimeMillis()-placed);

		if(scaleTime==0){
			return 1;
		} else if(time<scaleTime){
			return (float) (((scaleEnd-scaleStart)/(float)scaleTime)*time+scaleStart);
		} else{
			return (float) scaleEnd;
		}
	}
	public void free_id(){
		if(id==highestId){
			--highestId;
		}
	}
	static public int getHighestID(){
		return highestId;
	}
	public Location getLocation(){
		return location;
	}
	public boolean ghost(){
		return ghost;
	}
	public boolean loaded(){
		return loaded;
	}
	public boolean blocked(BlockPlaceEvent event)
	{
		String playerName=event.getPlayer().getName();
		PlayerReinforcement reinforcement = (PlayerReinforcement) Citadel.getReinforcementManager().getReinforcement(location.getBlock());
		if(reinforcement instanceof PlayerReinforcement){
			Faction owner = reinforcement.getOwner();
			if(owner.isMember(playerName)||owner.isFounder(playerName)||owner.isModerator(playerName)){
				//return false;
			}

			if (((event.getBlock().getX() - location.getX()) * (float)(event.getBlock().getX() - location.getX()) + 
					(event.getBlock().getZ() - location.getZ()) * (float)(event.getBlock().getZ() - location.getZ()) > radiusSquared)
					|| (event.getBlock().getY() <= location.getY())) {


				return false;
			}

		}
		return true;
	}
	public void handlePlaced(Block block) {
		block.breakNaturally();
		erode(erosionFromPlace());
	}
	public boolean shouldCull(){

		if(strength+balance> 0){
			return false;
		} else{
			return true;
		}
	}
	private void erode(float amount){
		long time=System.currentTimeMillis();
		int whole=(int) (amount);
		float fraction=(float) (amount-whole);
		float sumFraction=balance+fraction;
		whole=(int) (sumFraction+whole);
		fraction=(float) (sumFraction-whole);
		//Bastion.getPlugin().getLogger().info("min_break_time="+min_break_time+" Which means (time-lastPlace)>=min_break_time)="+((time-lastPlace)>=min_break_time)+" because (time-lastPlace)==("+time+"-"+lastPlace+")");
		if((time-lastPlace)>=min_break_time){
			IReinforcement reinforcement = Citadel.getReinforcementManager().getReinforcement(location.getBlock());
			if (reinforcement != null) {
				strength=reinforcement.getDurability();
				strength-=whole;
				balance=fraction;
				Bastion.getPlugin().getLogger().info("balance set to "+balance);

				Bastion.getPlugin().getLogger().info("Bastion at: "+location+" strength is now at "+strength+" after "+amount+" was removed because balance="+balance
						+",fraction="+fraction);
				reinforcement.setDurability(strength);
				Citadel.getReinforcementManager().addReinforcement(reinforcement);
			}
			lastPlace=time;

		}
		if(shouldCull()){
			close();
		}
	}
	public int getID(){
		return id;
	}

	public long getPlaced(){
		return placed;
	}
	public float getBalance(){
		return balance;
	}
	@Override
	public int qtXMin() {
		return location.getBlockX()-radius;
	}

	@Override
	public int qtXMid() {
		return location.getBlockX();
	}

	@Override
	public int qtXMax() {
		return location.getBlockX()+radius;
	}

	@Override
	public int qtZMin() {
		return location.getBlockZ()-radius;
	}

	@Override
	public int qtZMid() {
		return location.getBlockZ();
	}

	@Override
	public int qtZMax() {
		return location.getBlockZ()+radius;
	}

	@Override
	public int compareTo(Object o) {
		BastionBlock other=(BastionBlock)o;
		int thisX=location.getBlockX();
		int thisY=location.getBlockY();
		int thisZ=location.getBlockZ();

		int otherX=other.location.getBlockX();
		int otherY=other.location.getBlockX();
		int otherZ=other.location.getBlockX();

		if(thisX<otherX)
			return -1;
		if(thisY<otherY)
			return -1;
		if(thisZ<otherZ)
			return -1;

		if(thisX>otherX)
			return 1;
		if(thisY>otherY)
			return 1;
		if(thisZ>otherZ)
			return 1;

		return 0;
	}
}