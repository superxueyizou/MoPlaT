package agent;

import app.RVOModel;
import ec.util.MersenneTwisterFast;
import environment.RVOSpace;
import environment.geography.Goals;
import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import motionPlanners.VelocityCalculator;
import motionPlanners.pbm.WorkingMemory;
import motionPlanners.pbm.WorkingMemory.CommitToHighSpeed;
import motionPlanners.rbm.RuleBasedNew;
import motionPlanners.rvo.RVO_1_Standard;
import motionPlanners.rvo.RVO_1_WithAccel;
import motionPlanners.rvo.RVO_2_1;
import sim.engine.SimState;
import sim.portrayal.LocationWrapper;
import sim.util.Bag;
import sim.util.Proxiable;

public class RVOAgent extends AgentPortrayal implements Proxiable {

    protected final static boolean TRAILS = true;
    public final static boolean SHOW_VELOCITY = false;
    //static constants
    // public static final double MAXACCEL = 260.0f; //m/s*s
    public static final double INFO_LIMIT = 33.0f; // Chunks!
//    public static final double INFO_LIMIT = Double.MAX_VALUE; // Chunks!
    public final static float RADIUS = 0.15f;
    public final static double PREFERRED_SPEED = 1.3;
    public final static int SENSOR_RANGE = 100; //sensor range in proportion to agent radius
    public static int agentCount = 0; // number of agents
    protected int id;
    protected List<GoalLine> checkPoints;
    int currentGoal = 0;
    /**
     * This is used in the time to collision calculation to scale the radius of self
     * This factor is multiplied by the radius of the agent and added to the
     * original radius when calculating time to collision.
     *
     * i.e., an agent will consider itself to have a radius (r + personalSpaceFactor*r)
     *
     * This means agents will estimate collisions (i.e., non infinite time to collision)
     * for a circle bigger than their actual size.
     *
     * If this is zero then the standard RVO calculation for time to collision is performed
     */
    private double personalSpaceFactor = 1.5;
    /**
     * Current position of the agent from javax.vecmath
     */
    protected Point2d currentPosition;
    /**
     * Current velocity of the agent
     */
    protected Vector2d velocity;
    protected Vector2d chosenVelocity;
    /**
     * Agents preferred velocity, calculated each timestep according to goal
     * and current location
     */
    protected Vector2d prefVelocity;
    /**
     * Sets whether to display the agent's velocity on the map or not
     */
    protected double preferredSpeed = PREFERRED_SPEED;
    /**
     * Intermediate goal destination of agent
     */
    protected Point2d goal;
    /**
     * Environmental space of the agents, contains multiple MASON fields
     */
    protected RVOSpace mySpace;
    protected SenseThink st;
    protected Act a;
    /**
     * The motion planning system used by the agent, this can used any method
     * for motion planning that implements the VelocityCalculator interface
     */
    protected VelocityCalculator velocityCalc;
    protected CommitToHighSpeed commitmentLevel;

    public RVOAgent(RVOSpace mySpace) {
        super(RADIUS, TRAILS); //for portraying the trails on the agentportrayal layer
        this.mySpace = mySpace;
        currentPosition = new Point2d();
        goal = new Point2d();



        velocity = new Vector2d(findPrefVelocity());
        st = new SenseThink();
        a = new Act();

        if (RVOModel.MODEL == RVOModel.Model.RVO2) {
            velocityCalc = new RVO_2_1();
        } else if (RVOModel.MODEL == RVOModel.Model.PatternBasedMotion) {
            velocityCalc = new WorkingMemory(this);
        } else if (RVOModel.MODEL == RVOModel.Model.RVO1Standard) {
            velocityCalc = new RVO_1_Standard();
        } else if (RVOModel.MODEL == RVOModel.Model.RVO1Acceleration) {
            velocityCalc = new RVO_1_WithAccel();
        } else if (RVOModel.MODEL == RVOModel.Model.RuleBasedNew) {
            velocityCalc = new RuleBasedNew();
        }
        id = agentCount++;
        checkPoints = new ArrayList<GoalLine>();
    }

    public RVOAgent(RVOAgent otherAgent) {
        this(otherAgent.getMySpace());
        currentPosition = new Point2d(otherAgent.getCurrentPosition().getX(), otherAgent.getCurrentPosition().getY());
        goal = new Point2d(otherAgent.getGoal().getX(), otherAgent.getGoal().getY());
        velocity = new Vector2d(otherAgent.getVelocity());
        this.mySpace = otherAgent.mySpace;
        id = otherAgent.getId();
        agentCount--;
    }

    public RVOAgent(Point2d goal, RVOSpace mySpace) {
        this(mySpace);
        this.goal = goal;
    }

    public RVOAgent(Point2d startPosition, Point2d goal, RVOSpace mySpace, Color col) {
        this(mySpace);
        this.setColor(col);
        this.currentPosition = startPosition;
        this.goal = goal;
        this.findPrefVelocity();
        velocity = new Vector2d(this.prefVelocity);
        st = new SenseThink();
        a = new Act();

        if (RVOModel.MODEL == RVOModel.Model.RVO2) {
            this.velocityCalc = new RVO_2_1();
        } else if (RVOModel.MODEL == RVOModel.Model.PatternBasedMotion) {
            this.velocityCalc = new WorkingMemory(this);
        } else if (RVOModel.MODEL == RVOModel.Model.RVO1Standard) {
            this.velocityCalc = new RVO_1_Standard();
        } else if (RVOModel.MODEL == RVOModel.Model.RVO1Acceleration) {
            this.velocityCalc = new RVO_1_WithAccel();
        } else if (RVOModel.MODEL == RVOModel.Model.RuleBasedNew) {
            this.velocityCalc = new RuleBasedNew();
        }
    }

    public Point2d getGoal() {
        return goal;
    }

    public void setGoal(Point2d goal) {
        this.goal = goal;
        findPrefVelocity();
        velocity = new Vector2d(this.prefVelocity);
    }

    private boolean reachedGoal() {
        return (currentPosition.distance(goal) < radius);
    }

    public Point2d getCurrentPosition() {
        return currentPosition;
    }

    public double getX() {
        return currentPosition.getX();
    }

    public double getY() {
        return currentPosition.getY();
    }

    public void setCurrentPosition(double x, double y) {
        currentPosition.setX(x);
        currentPosition.setY(y);
    }

    public Vector2d getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector2d prefVel) {
        velocity = new Vector2d(prefVel);
    }

    public double getSpeed() {
        return velocity.length();
    }

    public void setSpeed(double speed) {
        velocity.normalize();
        velocity.scale(speed);
    }

    public final Vector2d findPrefVelocity() {
        if (checkPoints == null || (!RVOModel.LATTICEMODEL)) {
            prefVelocity = new Vector2d(goal);
        } else if (currentGoal > checkPoints.size()) {
            return prefVelocity;
        } else {
            Vector2d distance = new Vector2d(checkPoints.get(currentGoal).getCenter());
            distance.sub(currentPosition);


            if (this.getCurrentPosition().getY() < (checkPoints.get(currentGoal).getCenter().getY())) {
                if (currentGoal < checkPoints.size()) {
                    currentGoal++;
                }

                if (currentGoal == checkPoints.size()) {
                    currentPosition = new Point2d(-4000, 4000);
                    goal = new Point2d(-4000, 4000);
                    currentGoal++;

                    return prefVelocity;
                }
            } else if (this.getCurrentPosition().getX() > (checkPoints.get(currentGoal).getStart().getX() + RVOAgent.RADIUS)
                    && this.getCurrentPosition().getX() < (checkPoints.get(currentGoal).getEnd().getX() - RVOAgent.RADIUS)) {
                checkPoints.get(currentGoal).center.x = this.getCurrentPosition().getX();
            }
            prefVelocity = new Vector2d(checkPoints.get(currentGoal).getCenter());
        }

        prefVelocity.sub(currentPosition);
        prefVelocity.normalize();

        prefVelocity.scale(preferredSpeed);
        return prefVelocity;
    }

    public double getRadius() {
        return radius;
    }

    public double getPersonalSpaceFactor() {
        return personalSpaceFactor;
    }

    public RVOSpace getMySpace() {
        return mySpace;
    }

    public void setMySpace(RVOSpace space) {
        this.mySpace = space;
    }

    public void scheduleAgent() {
        this.mySpace.getRvoModel().schedule.scheduleRepeating(st, 2, 1.0);
        this.mySpace.getRvoModel().schedule.scheduleRepeating(a, 3, 1.0);
    }

    /**
     * Returns the predicted position i time steps in the future based on linear dead reckoning.
     *
     * @param i
     * @return
     */
    public Point2d getNextPosition(int i) {
        Point2d predictPos = new Point2d(this.getVelocity());
        predictPos.scale(i * RVOModel.TIMESTEP);
        predictPos.add(this.currentPosition);
        return predictPos;
    }

    public int getId() {
        return id;
    }

    public void setPreferredSpeed(double preferredSpeed) {
        this.preferredSpeed = preferredSpeed;
    }

    public void setCheckPoints(ArrayList<Goals> goals) {
        checkPoints = new ArrayList(goals.size());
        GoalLine tempGoalLine;

        for (int i = 0; i < goals.size(); i++) {
            tempGoalLine = new GoalLine(goals.get(i).getVertices().get(0).getX(),
                    goals.get(i).getVertices().get(0).getY(),
                    goals.get(i).getVertices().get(1).getX(),
                    goals.get(i).getVertices().get(1).getY());



            this.checkPoints.add(tempGoalLine);
        }


    }

    @Override
    public String getName(LocationWrapper wrapper) {
        return "Agent " + id;
    }

    class SenseThink extends RemovableStep {

        public void step(SimState ss) {
            if (!dead) {
                if (reachedGoal()) {
                    dead = true;
                    return;
                }
                findPrefVelocity(); //update the preferredVelocity according to the current position and the goal

                // Randomize movement for lattice model... uncomment to implement.. might be better to flag
                if (RVOModel.LATTICEMODEL == true) {
//                    MersenneTwisterFast random = new MersenneTwisterFast();
//                    double chosenSpeed = prefVelocity.length();
//                    double randomnessFactor = chosenSpeed * (random.nextDouble() - 0.5) * 2.0;
//
//                    if (random.nextDouble() < 0.33) {
//                        prefVelocity.setX(prefVelocity.x + randomnessFactor);
//                    } else if (random.nextDouble() < 0.67) {
//                        prefVelocity.setY(prefVelocity.y + randomnessFactor);
//                    }
                }

                chosenVelocity = new Vector2d(prefVelocity);


                Bag sensedNeighbours = mySpace.senseNeighbours(RVOAgent.this);


                if (RVOModel.INFOPROCESSING) {
                    /**
                     * Here we process the neighbour list that was passed to it to determine collisions
                     */
                    List<RVOAgent> sortedList = new ArrayList<RVOAgent>();
                    



                    determineInitialLists(sortedList, sensedNeighbours);




                    sensedNeighbours.clear();


                    sensedNeighbours.addAll(sortedList);


                }
                //Don't put obstacles as Null.. instead initialise an empty set. NullPointerException will result.
                chosenVelocity = velocityCalc.calculateVelocity(RVOAgent.this, sensedNeighbours, mySpace.senseObstacles(RVOAgent.this),
                        prefVelocity, RVOModel.TIMESTEP);
                //  for(int j=0;j<mySpace.senseNeighbours(RVOAgent.this).numObjs;j++)
                //      System.out.println(j+".opp cluster for: "+RVOAgent.this.getId()+"is" + ((RVOAgent)(mySpace.senseNeighbours(RVOAgent.this).get(j))).getCurrentPosition());
            }
        }

        private double assignDistanceScore(Vector2d distanceVector, RVOAgent tempAgent) {

            double distance = (distanceVector.length() - tempAgent.getRadius() - RVOAgent.this.RADIUS);
            if (distance < 0) {
                return 1.5; //1.5? interesting effect
            }
//            double distanceScore = RVOAgent.INFO_LIMIT- (RVOAgent.INFO_LIMIT/10.0)*Math.log((RVOAgent.INFO_LIMIT/10.0)*distanceVector.length());
            double distanceScore = Math.max(Math.min(1.0f, (Math.expm1(5.0f / distance) - 0.11)), 0.1);

            Vector2d angleFormedVector = new Vector2d(RVOAgent.this.getGoal());
            angleFormedVector.sub(RVOAgent.this.currentPosition);
            double angleFormed = angleFormedVector.dot(distanceVector);
            double angleScore = 0.0f;
            if (angleFormed < 0) {
                angleScore = 0.1f;
            } else {
                angleFormed = angleFormed / (distanceVector.length() * angleFormedVector.length());
                angleFormed = Math.acos(angleFormed);


                if (angleFormed < Math.PI / 3.0f) {
                    angleScore = 1.0f;

                } else if (angleFormed < 4.0f * Math.PI / 9.0f) {
                    angleScore = (8.1f * (3 * angleFormed - Math.PI) / (Math.PI)) + 0.1f;
                } else {
                    angleScore = 0.1f;
                }
            }
//            if (RVOAgent.this.getId() == 0) {
//                System.out.println(tempAgent.getCurrentPosition().getX() + "," + angleFormed * 180.0f / Math.PI + " as= " + angleScore);
//                //    System.out.println(distanceScore+" ds= "+distanceScore);
//            }

//            return (distanceScore + angleScore) / 2.0f;
            return (distanceScore > 1.0 ? distanceScore : distanceScore * angleScore);
        }

        private void determineInitialLists(List<RVOAgent> sortedList, Bag sensedNeighbours) {
            List<Double> distanceScoreList = new ArrayList<Double>();
            for (int i = 0; i < sensedNeighbours.size(); i++) {
                RVOAgent tempAgent = (RVOAgent) sensedNeighbours.get(i);
                Vector2d distanceVector = new Vector2d(tempAgent.getCurrentPosition());
                distanceVector.sub(RVOAgent.this.getCurrentPosition());

//                        double distance = distanceVector.length();
                double distanceScore = assignDistanceScore(distanceVector, tempAgent);
                boolean added = false;
                int j = 0;
//                        if (tempAgent.getCurrentPosition().getX() < 2.5) {
//                            System.out.println("test");
//                        }
                for (; j < distanceScoreList.size(); j++) {

                    if (tempAgent.getCurrentPosition().equals( // don't sense the same element
                            RVOAgent.this.getCurrentPosition())
                            || (tempAgent.getCurrentPosition().equals(
                            sortedList.get(j).getCurrentPosition())
                            && tempAgent.getRadius() == sortedList.get(j).getRadius())) {
                        added = true;
                        break;
                    }


                    if (distanceScoreList.get(j).compareTo(new Double(distanceScore)) < 0) { // this item is closer than exisiting
                        distanceScoreList.add(j, new Double(distanceScore));      // insert the element into the list
                        sortedList.add(j, tempAgent);
                        added = true;
                        break;
                    } else if (distanceScoreList.get(j).equals(distanceScore)) { // this item is closer than exisiting
                        if (tempAgent.getVelocity().dot(sortedList.get(j).getVelocity()) < 0) {
                            if (tempAgent.getVelocity().dot(RVOAgent.this.getVelocity()) < 0) {
                                // the currently sensed agent is moving in the opposite direction of my agent

                                distanceScoreList.add(j, new Double(distanceScore));      // insert the element into the list
                                sortedList.add(j, tempAgent);
                                added = true;
                                break;
                            } else {
                                continue;
                            }
                        }

                        Vector2d distanceCheck = new Vector2d(sortedList.get(j).getCurrentPosition());
                        distanceCheck.sub(RVOAgent.this.getCurrentPosition());
                        double sortedDistance = distanceCheck.length() - sortedList.get(j).getRadius();
                        double tempDistance = distanceVector.length() - tempAgent.getRadius();

                        if (tempDistance < sortedDistance) {

                            distanceScoreList.add(j, new Double(distanceScore));      // insert the element into the list
                            sortedList.add(j, tempAgent);
                            added = true;
                            break;
                        }
                    }
                }
                if (!added) {
                    distanceScoreList.add(j, new Double(distanceScore));      // insert the element into the list
                    sortedList.add(j, tempAgent);
                }
            }
            for (int j = 0; j < sortedList.size(); j++) {
                if (sortedList.get(j).getRadius() > 0.16) {
                    RVOAgent clusteredAgent = (RVOAgent) sortedList.get(j);
                    for (int k = 0; k < sortedList.size(); k++) {
                        if (j != k) {
                            RVOAgent tempAgent = (RVOAgent) sortedList.get(k);
                            Vector2d distanceCheck = new Vector2d(clusteredAgent.getCurrentPosition());
                            distanceCheck.sub(tempAgent.getCurrentPosition());
                            if (distanceCheck.length() + tempAgent.getRadius() <= clusteredAgent.getRadius()) {
                                sortedList.remove(k);
                                distanceScoreList.remove(k);
                                k--;
                            }
                        }
                    }
                }
            }
            double accumulatedScore = 0.0;
            int indexToBeDeleted = 0;
            for (; indexToBeDeleted < distanceScoreList.size(); indexToBeDeleted++) {
                accumulatedScore += distanceScoreList.get(indexToBeDeleted);
                if (accumulatedScore >= RVOAgent.INFO_LIMIT) {
                    break;
                }
            }
            for (int j = distanceScoreList.size() - 1; j > indexToBeDeleted; j--) {
                distanceScoreList.remove(indexToBeDeleted);
                sortedList.remove(j);
            }
        }
    }

    class Act extends RemovableStep {

        public void step(SimState ss) {
            if (!dead) {
                if (reachedGoal()) {
                    dead = true;
                    return;
                }
                velocity = chosenVelocity;

                double currentPosition_x = (currentPosition.getX()
                        + velocity.getX() * RVOModel.TIMESTEP);
                double currentPosition_y = (currentPosition.getY()
                        + velocity.getY() * RVOModel.TIMESTEP);
                setCurrentPosition(currentPosition_x, currentPosition_y);

                getMySpace().updatePositionOnMap(RVOAgent.this, currentPosition_x,
                        currentPosition_y);


            }
        }
    }

    private static class GoalLine {

        Point2d start;
        Point2d end;
        Point2d center;

        public GoalLine(double x1, double y1, double x2, double y2) {
            /**
             * CAREFUL : HARDCODED FOR SCENARIOS LIKE THIS
             *
             */
            start = new Point2d(x1, y1);
            end = new Point2d(x2, y2);
            center = new Point2d((x1 + x2) / 2.0, (y1 + y2) / 2.0);
        }

        public Point2d getStart() {
            return start;
        }

        public Point2d getEnd() {
            return end;
        }

        public Point2d getCenter() {
            return center;
        }
    }

    public class MyProxy {

        public Vector2d getVelocity() {
            return velocity;
        }
    }

    @Override
    public Object propertiesProxy() {
        return new MyProxy();
    }

    public VelocityCalculator getRvoCalc() {
        return velocityCalc;
    }

    public Vector2d getPrefVelocity() {
        return prefVelocity;
    }

    public CommitToHighSpeed getCommitementLevel() {
        return commitmentLevel;
    }

    public void setCommitmentLevel(final int number) {
        switch (number) {
            case 1:
                this.commitmentLevel = commitmentLevel.LOWCOMMITMENT;
                break;
            case 2:
                this.commitmentLevel = commitmentLevel.MIDCOMMITMENT;
                break;
            case 3:
                this.commitmentLevel = commitmentLevel.HIGHCOMMITMENT;
                break;

        }

    }
}
