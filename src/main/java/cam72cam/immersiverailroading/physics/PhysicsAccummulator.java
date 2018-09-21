package cam72cam.immersiverailroading.physics;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.util.Speed;

public class PhysicsAccummulator {
	//http://evilgeniustech.com/idiotsGuideToRailroadPhysics/HorsepowerAndTractiveEffort/
	//http://www.republiclocomotive.com/locomotive-power-calculations.html
	//http://www.wplives.org/forms_and_documents/Air_Brake_Principles.pdf

	public double tractiveEffortNewtons = 0;
	public double airBrake = 0;
	//lbs
	public double rollingResistanceNewtons = 0;
	public double gradeForceNewtons = 0;
	public double massToMoveKg = 0;
	public double brakeAdhesionNewtons = 0;
	public int count = 0;
	private TickPos pos;
	
	public PhysicsAccummulator(TickPos pos) {
		this.pos = pos;
	}

	public void accumulate(EntityRollingStock stock, Boolean direction) {
		count++;
		
		massToMoveKg += stock.getWeight();
		
		if (!(stock instanceof EntityMoveableRollingStock)){
			return;
		}
		
		EntityMoveableRollingStock movable = ((EntityMoveableRollingStock)stock);
		
		// SHOULD THIS HAVE DIRECTION MULT?
		double stockMassKg = stock.getWeight();
		rollingResistanceNewtons += 0.57 * (stockMassKg * 10); //0.57 represence the Friction between track and train
		
		// SHOULD THIS HAVE DIRECTION MULT?
		double grade = -Math.tan(Math.toRadians(pos.rotationPitch % 90)) * Config.ConfigBalance.slopeMultiplier;
		// lbs * 1%gradeResistance * grade multiplier
		gradeForceNewtons += ((stockMassKg * 10) / tan(grade));
		
		if (stock instanceof Locomotive) {
			Locomotive loco = (Locomotive) stock;
			tractiveEffortNewtons += loco.getTractiveEffortNewtons(pos.speed) * (direction ? 1 : -1);
			airBrake += Math.min(1, Math.pow(loco.getAirBrake() * loco.getDefinition().getBrakePower(), 2)) * loco.slipCoefficient();
			brakeAdhesionNewtons += loco.getDefinition().getStartingTractionNewtons(stock.gauge); 
		} else {
			// Air brake only applies 1/4th
			// 0.25 = steel wheel on steel rail	
			brakeAdhesionNewtons += ((tractiveEffortNewtons / massToMoveKg) - pos.speed.minecraft()) / pos.speed.minecraft(); //Changed so the wheel adhesion depends on TrainsSpeed and WheelSpeed
		}
		
		int slowdown = movable.getSpeedRetarderSlowdown(pos);
		rollingResistanceNewtons += (stockMassKg * 10) * 0.57; //0.57 as Steel on Steel RollingRestistance
	}
	
	public Speed getVelocity() {
		double airBrakeNewtons = brakeAdhesionNewtons * Math.min(airBrake, 1) * Config.ConfigBalance.brakeMultiplier;
		
		// a = f (to newtons) * m (to newtons)
		double tractiveAccell = tractiveEffortNewtons / massToMoveKg;
		double resistanceAccell = rollingResistanceNewtons / massToMoveKg;
		double gradeAccell = gradeForceNewtons / massToMoveKg;
		double brakeAccell = airBrakeNewtons / massToMoveKg;
		
		double currentMCVelocity = pos.speed.minecraft();
		double deltaAccellTractiveMCVelocity = Speed.fromMetric(tractiveAccell).minecraft();
		
		
		double deltaAccellGradeMCVelocity = Speed.fromMetric(gradeAccell).minecraft();
		
		double deltaAccellRollingResistanceMCVelocity = Speed.fromMetric(resistanceAccell).minecraft();
		double deltaAccellBrakeMCVelocity = Speed.fromMetric(brakeAccell).minecraft();
		// Limit decell to current speed to trains stop
		// Apply in the reverse direction of current travel
		double deltaDecell = -1 * Math.copySign(Math.min(Math.abs(currentMCVelocity), deltaAccellRollingResistanceMCVelocity +  deltaAccellBrakeMCVelocity), currentMCVelocity);
		
		double newMCVelocity = currentMCVelocity + deltaAccellTractiveMCVelocity + deltaAccellGradeMCVelocity + deltaDecell;

		if (Math.abs(newMCVelocity) < 0.001) {
			newMCVelocity = 0;
		}
		
		return Speed.fromMinecraft(newMCVelocity);
	}
}
