package agent.latticegas;

import agent.RVOAgent;
import app.PropertySet;
import app.RVOModel;
import ec.util.MersenneTwisterFast;
import environment.geography.Goals;
import environment.geography.Obstacle;
import environment.geography.Position;
import java.util.ArrayList;
import java.util.HashMap;
import javax.vecmath.Point2d;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.grid.IntGrid2D;
import sim.util.Int2D;

/**
 * This class implements the lattice gas model. There will be only one agent
 * which represents the CA structure that is used for a lattice gas model.
 *
 *
 * @author Vaisagh
 */
public class LatticeSpace {

    public static double DRIFT = 2.0;
    public static final double LATTICEGRIDSIZE = 2.0 * RVOAgent.RADIUS;
    protected int numGridX;
    protected int numGridY;
    protected MersenneTwisterFast random;
    protected double driftX;
    protected double driftY;
    protected int directionX;
    protected int directionY;
    protected int numberOfAgents;
    /**
     * This is the only space used by this model. 0 = freeSpace 1 = agent 2 =
     * obstacle
     */
    protected IntGrid2D space;
    /**
     * This is the model for local reference
     */
    protected RVOModel rvoModel;
    private ArrayList<GoalLines> goals;
    private int timeStepsPerMovement;
    private final int[][] previousField;

    public LatticeSpace(int xSize, int ySize, RVOModel rm) {

        /**
         * XSize and Ysize are specified in meters
         */
        numGridX = (int) Math.round(xSize / LATTICEGRIDSIZE);
        numGridY = (int) Math.round(ySize / LATTICEGRIDSIZE);
        space = new IntGrid2D(numGridX, numGridY);

        for (int i = 0; i < numGridX; i++) {
            for (int j = 0; j < numGridY; j++) {
                space.set(i, j, 0);
            }
        }
        rvoModel = rm;
        random = rvoModel.random;
        goals = new ArrayList<GoalLines>();

        numberOfAgents = 0;
        previousField = new int[space.field.length][space.field[0].length];
    }

    public Int2D generateRandomLocation() {
        int x = (int) (this.rvoModel.random.nextDouble() * (space.getWidth()));
        int y = (int) (this.rvoModel.random.nextDouble() * (space.getHeight()));
        return new Int2D(x, y);
    }

    public void addAgentAt(Double x, Double y, int id) {
        space.set((int) Math.ceil((x - (LATTICEGRIDSIZE / 2)) / LATTICEGRIDSIZE),
                (int) Math.ceil((y - (LATTICEGRIDSIZE / 2)) / LATTICEGRIDSIZE), id + 10);
        numberOfAgents++;
    }

    /**
     * Changing to just being the outline of an obstacle willhave to check later
     * if there is any problem with doing this.
     *
     * @param tempObst
     */
    public void addObstacle(Obstacle tempObst) {
        for (int i = 0; i < tempObst.getVertices().size(); i++) {
            Position currentVertex = tempObst.getVertices().get(i);
            Position nextVertex = tempObst.getVertices().get((i + 1) % tempObst.getVertices().size());

            int x1 = (int) Math.floor(currentVertex.getX() / LATTICEGRIDSIZE);
            int x2 = (int) Math.floor(nextVertex.getX() / LATTICEGRIDSIZE);
            int y1 = (int) Math.floor(currentVertex.getY() / LATTICEGRIDSIZE);
            int y2 = (int) Math.floor(nextVertex.getY() / LATTICEGRIDSIZE);

//            assert (x1 == x2) || (y1 == y2);

            if (x1 == x2) {
                int k = x1;
                int startY = Math.min(y1, y2);
                int endY = Math.max(y1, y2);
                for (int j = startY; j <= endY; j++) {
                    space.set(k, j, 2);
                }
            } else if (y1 == y2) {
                int j = y1;
                int startX = Math.min(x1, x2);
                int endX = Math.max(x1, x2);
                for (int k = startX; k <= endX; k++) {
                    space.set(k, j, 2);
                }
            } else {
                int mnx = Math.min(x1, x2);
                int mxx = Math.max(x1, x2);
                int mny = Math.min(y1, y2);
                int mxy = Math.max(y1, y2);
                int slopeY = (mxy - mny) / (mxx - mnx);
                int slopeX = (mxx - mnx) / (mxy - mny);
                if ((x1 - x2) * (y1 - y2) > 0) {
                    int k = mnx, j = mny;
                    if (slopeY > 0) {
                        for (; k <= mxx && j <= mxy; k++) {
                            space.set(k, j, 2);
                            if (k % slopeY == 0) {
                                j++;
                            }
                        }
                    } else {
                        for (; k <= mxx && j <= mxy; j++) {
                            space.set(k, j, 2);
                            if (j % slopeX == 0) {
                                k++;
                            }
                        }
                    }
                    while (k <= mxx) {
                        space.set(k, j, 2);
                        k++;
                    }
                    while (j <= mxy) {
                        space.set(k, j, 2);
                        j++;
                    }
                } else {
                    int k = mnx, j = mxy;
                    if (slopeY > 0) {
                        for (; k <= mxx && j >= mny; k++) {
                            space.set(k, j, 2);
                            if (k % slopeY == 0) {
                                j--;
                            }
                        }
                    } else {
                        for (; k <= mxx && j >= mny; j--) {
                            space.set(k, j, 2);
                            if (j % slopeX == 0) {
                                k++;
                            }
                        }
                    }
                    while (k <= mxx) {
                        space.set(k, j, 2);
                        k++;
                    }
                    while (j >= mny) {
                        space.set(k, j, 2);
                        j--;
                    }
                }
            }

        }

    }

    public Object getSpace() {
        return space;
    }

    public void scheduleLattice() {
        rvoModel.schedule.scheduleRepeating(new LatticeStep(), 1.0);
    }

    public void addGoal(Goals goal) {
        GoalLines tempGoal = new GoalLines(
                goal.getStartPoint().getX(), goal.getStartPoint().getY(),
                goal.getEndPoint().getX(), goal.getEndPoint().getY());
        goals.add(tempGoal);
    }

    public synchronized int[][] getField() {
        int[][] result = new int[space.field.length][space.field[0].length];
        for (int i = 0; i < space.field.length; i++) {
            System.arraycopy(space.field[i], 0, result[i], 0, space.field[0].length);
        }
        return result;
//        return this.previousField;
    }

    public synchronized ArrayList<Point2d> getAgentLocationList() {
        ArrayList<Point2d> agentLocationList = new ArrayList<Point2d>();

        for (int i = 0; i < space.field.length; i++) {
            for (int j = 0; j < space.field[0].length; j++) {
                if ((space.get(i, j)) == 1) {
                    agentLocationList.add(new Point2d(i, j));

                }
            }

        }
        return agentLocationList;
    }

    public synchronized HashMap<Integer, Point2d> getAgentLocationMap() {
        HashMap<Integer, Point2d> agentLocationMap = new HashMap<Integer, Point2d>();

        for (int i = 0; i < space.field.length; i++) {
            for (int j = 0; j < space.field[0].length; j++) {
                if ((space.get(i, j)) > 2) {
                    agentLocationMap.put(space.get(i, j) - 10, new Point2d(i, j));

                }
            }

        }
        return agentLocationMap;
    }

    public void setDirection(int direction) {
        switch (direction) {
            case 0:
                directionX = -1;
                directionY = 0;
                break;
            case 1:
                directionX = 1;
                directionY = 0;
                break;
            case 2:
                directionX = 0;
                directionY = 1;
                break;
            case 3:
                directionX = 0;
                directionY = -1;
                break;
        }

    }

    public void setSpeed(double speed) {
        final double distanceMovedInOneStep = RVOAgent.RADIUS * 2.0;
        final double numberOfTimeStepsInOneSecond = 1.0 / PropertySet.TIMESTEP;
        this.timeStepsPerMovement = (int) ((distanceMovedInOneStep * numberOfTimeStepsInOneSecond) / speed);
    }

    public boolean isEmpty() {
        return numberOfAgents == 0;
    }

    public int getNumberOfAgents() {
        return numberOfAgents;
    }

    private static class GoalLines {

        Point2d start;
        Point2d end;

        public GoalLines(double x1, double y1, double x2, double y2) {
            /**
             * CAREFUL : HARDCODED FOR SCENARIOS LIKE THIS
             *
             */
            y1 = (y1 < 0 ? 0 : y1);
            y2 = (y2 < 0 ? 0 : y2);
            start = new Point2d(x1 / LatticeSpace.LATTICEGRIDSIZE, y1 / LatticeSpace.LATTICEGRIDSIZE);
            end = new Point2d((x2 / LatticeSpace.LATTICEGRIDSIZE) - 1, y2 / LatticeSpace.LATTICEGRIDSIZE);
        }

        public Point2d getStart() {
            return start;
        }

        public Point2d getEnd() {
            return end;
        }

        private int YdistanceTo(int i, int j) {
            int yDistance;
            if (j >= start.y && j <= end.y) {
                return Integer.MAX_VALUE;
            } else if (j < start.y) {
                yDistance = (int) (start.y - j);
            } else {
                yDistance = (int) (j - end.y);
            }

            return yDistance;
        }

        private int XdistanceTo(int i, int j) {
            int xDistance;
            if (i >= start.x && i <= end.x) {
                return Integer.MAX_VALUE;
            } else if (i < start.x) {
                xDistance = (int) (start.x - i);
            } else {
                xDistance = (int) (i - end.x);
            }


            return xDistance;

        }
    }

    class LatticeStep implements Steppable {

        @Override
        public void step(SimState ss) {
            if (ss.schedule.getSteps() % LatticeSpace.this.timeStepsPerMovement != 0) {
                return;
            }
            assert DRIFT >= 0;



            for (int i = 0; i < space.field.length; i++) {
                for (int j = 0; j < space.field[0].length; j++) {
                    previousField[i][j] = space.get(i, j);
                    space.set(i, j, -1);
                }
            }

            for (int i = 0; i < space.field.length; i++) {
                for (int j = 0; j < space.field[0].length; j++) {
                    if ((j == 0 && directionY == -1)
                            || (j == space.getHeight() - 1 && directionY == 1)
                            || (i == 0 && directionX == -1)
                            || (j == space.getWidth() - 1 && directionX == 1)) {
                        // Goal reached
                        if (previousField[i][j] > 2) {
                            space.set(i, j, 0);
//                            numberOfAgents--;
                        }
//                        System.out.println(numberOfAgents);
                    } else if (previousField[i][j] == 2) {
                        // if it is an obstacle it
                        //remains an obstacle
                        space.set(i, j, 2);
                    }
                }
            }
            for (int j = 0; j < space.field[0].length; j++) {
                updateMatrix(51, 52, j, previousField, space);
            }
            for (int j = 0; j < space.field[0].length; j++) {
                updateMatrix(50, -1, j, previousField, space);
                updateMatrix(52, space.field[0].length, j, previousField, space);

            }
            numberOfAgents = 0;

            for (int i = 0;
                    i < space.field.length;
                    i++) {
                for (int j = 0; j < space.field[0].length; j++) {
                    if (space.get(i, j) == -1) {
                        space.set(i, j, 0);
                    }
                    if (space.get(i, j) > 2) {
//                        System.out.println(i+","+j);
                        numberOfAgents++;
                    }
                }
//                System.out.println();
            }
//            System.out.println(numberOfAgents);
        }

        private void updateMatrix(int start, int stop, int j, int[][] previousField, IntGrid2D space) {
            // Traverse through lattice
            int myDirectionX = 0, myDirectionY = 0;
            int goalYCenter = 0;
            int goalXCenter = 0;
            int goalXStart = 0;
            int goalYStart = 0;
            int goalXEnd = 0;
            int goalYEnd = 0;
            int sign = (stop - start) / Math.abs(stop - start);
            for (int i = start; i != stop; i = i + sign) {
                if (previousField[i][j] > 2) {
                    //if it used to be an agent
                    if (space.get(i, j) > 2) {
                        //if it has already been set to be an agent then let it be an agent.
                            /*
                         * TODO: It is interesting to note that this whole
                         * algo is very likely to get totally messed up when
                         * the orientation of the space is changed because i
                         * sort of assume that it is processed form left to right
                         */
                        continue;
                    }

                    boolean breakable = false; // FLAG TO BREAK FROM LOOP
                    if (directionX != 0) {
                        //IF THE AGENTS ARE SUPPOSED TO BE EVACUATING TO THE RIGHT OR LEFT
                        assert false;


                    } else {
                        //HERE THE AGENTS ARE SUPPOSED TO BE MOVING UP OR DOWN

                        //Search for the closest goal in the direction of propagation
//                            for (int p = j + directionY; p >= 0 && p < space.getHeight(); p += directionY) {
                        int minDistanceY;
                        minDistanceY = Integer.MAX_VALUE;
                        int minDistanceX;
                        minDistanceX = Integer.MAX_VALUE;
//                                GoalLines chosenGoal = null;
                        for (GoalLines tempGoal : goals) {
                            if (j < tempGoal.start.y) {
                                continue;
                            }
                            int distanceY = tempGoal.YdistanceTo(i, j);
                            if (distanceY < minDistanceY) {

                                goalXStart = (int) Math.round(tempGoal.getStart().getX());
                                goalYStart = (int) Math.round(tempGoal.getStart().getY());
                                goalXEnd = (int) Math.round(tempGoal.getEnd().getX());
                                goalYEnd = (int) Math.round(tempGoal.getEnd().getY());
                                minDistanceY = distanceY;
                                minDistanceX = tempGoal.XdistanceTo(i, j);
//                                     System.out.println(" Comparing p= " + p + "with " + goals.get(q).getStart().getY());
//                                        break;
                            } else if (distanceY == minDistanceY) {
                                int distanceX = tempGoal.XdistanceTo(i, j);
                                if (distanceX < minDistanceX) {

                                    goalXStart = (int) Math.round(tempGoal.getStart().getX());
                                    goalYStart = (int) Math.round(tempGoal.getStart().getY());
                                    goalXEnd = (int) Math.round(tempGoal.getEnd().getX());
                                    goalYEnd = (int) Math.round(tempGoal.getEnd().getY());
                                    minDistanceY = distanceY;
                                    minDistanceX = distanceX;
//                                     System.out.println(" Comparing p= " + p + "with " + goals.get(q).getStart().getY());
//                                        break;}
                                }
                            }
//                                System.out.println(minDistance);
//                                if (breakable) {
//                                    break;
//                                }
//                            }

                        }

//                        assert breakable == true;
                        goalYCenter = (goalYStart + goalYEnd) / 2;
                        goalXCenter = (goalXStart + goalXEnd) / 2;

                        driftX = DRIFT * (double) (Math.abs(i - goalXCenter) / (double) ((Math.abs(i - goalXCenter)) + (Math.abs(j - goalYCenter))));
                        driftY = DRIFT * (double) (Math.abs(j - goalYCenter) / (double) ((Math.abs(i - goalXCenter)) + (Math.abs(j - goalYCenter))));


                        if (goalXStart != goalXEnd) {
                            myDirectionY = myDirectionX = 0;
                            if (goalYStart > j) {
                                myDirectionY = 1;
                            } else if (goalYStart < j) {
                                myDirectionY = -1;
                            }
//                            if (i > goalXStart && i < goalXEnd) {
//
//                                myDirectionX = 0;
//                                goalX = i;
//
//                            } else {


//                                System.out.println(goalXCenter);
                            if (i < goalXCenter) {
                                myDirectionX = 1;

                            } else if (i > goalXCenter) {
                                myDirectionX = -1;
                            }

                            //                         }
//                            System.out.println("My direction y  =" + myDirectionY);
//                            System.out.println("My direction x  =" + myDirectionX);
                            /**
                             * Now to check where to move: since it is
                             * horizontal door i.e. fixed Y -myDirectionY is
                             * not considered
                             */
                            if ((space.get(i + myDirectionX, j) > 2) || (previousField[i + myDirectionX][j] == 2) || ((space.get(i + myDirectionX, j) == -1) && (previousField[i + myDirectionX][j] > 2))) {
                                if ((space.get(i - myDirectionX, j) > 2) || (previousField[i - myDirectionX][j] == 2) || ((space.get(i - myDirectionX, j) == -1) && (previousField[i - myDirectionX][j] > 2))) {
                                    if ((space.get(i, j + myDirectionY) > 2) || (previousField[i][j + myDirectionY] == 2) || ((space.get(i, j + myDirectionY) == -1) && (previousField[i][j + myDirectionY] > 2))) {
                                        //All three are obstacles
                                        //so keep agent at current position
//                                        System.out.println("Stationary ");
                                        space.set(i, j, previousField[i][j]);

                                    } else {
                                        //Obstacles on two sides
                                        //Can move forward
//                                        System.out.println("Forward definite");
                                        space.set(i, j + myDirectionY, previousField[i][j]); // move it forward
                                        space.set(i, j, 0); // set the current space to 0
                                    }



                                } else {
                                    if ((space.get(i, j + myDirectionY) > 2) || (previousField[i][j + myDirectionY] == 2) || ((space.get(i, j + myDirectionY) == -1) && (previousField[i][j + myDirectionY] > 2))) {
                                        //up and positive directionx are obstacles
                                        //move in megativeX Direction
//                                        System.out.println("Left definite");
                                        space.set(i - myDirectionX, j, previousField[i][j]); // move it forward
                                        space.set(i, j, 0); // set the current space to 0



                                    } else {
                                        //obstacle in positiveXdirection
                                        // move negative x or positive Y

                                        double p = random.nextDouble();

                                        if (p < ((1.0 - DRIFT) / 2.0)) {
                                            //negative X direction
                                            space.set(i - myDirectionX, j, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("Left random");
                                        } else {
                                            //positive Y direction
                                            space.set(i, j + myDirectionY, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("forward random");
                                        }
                                    }
                                }
                            } else {
                                if ((space.get(i - myDirectionX, j) > 2) || (previousField[i - myDirectionX][j] == 2) || ((space.get(i - myDirectionX, j) == -1) && (previousField[i - myDirectionX][j] > 2))) {
                                    if ((space.get(i, j + myDirectionY) > 2) || (previousField[i][j + myDirectionY] == 2) || ((space.get(i, j + myDirectionY) == -1) && (previousField[i][j + myDirectionY] > 2))) {
                                        //up and negative directionx are obstacles
                                        //move in positiveX Direction
                                        space.set(i + myDirectionX, j, previousField[i][j]); // move it forward
                                        space.set(i, j, 0); // set the current space to 0

//                                        System.out.println("right fixed");
                                    } else {

                                        //only negative direction X is an obstacle
                                        // move positive x or positive Y

                                        double p = random.nextDouble();

                                        if (p < (driftX + (1.0 - DRIFT) / 2.0)) {
                                            //positive X direction
                                            space.set(i + myDirectionX, j, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("right random out of front and right");

                                        } else {
                                            //positive Y direction
                                            space.set(i, j + myDirectionY, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("forward random");
                                        }
                                    }
                                } else {
                                    if ((space.get(i, j + myDirectionY) > 2) || (previousField[i][j + myDirectionY] == 2) || ((space.get(i, j + myDirectionY) == -1) && (previousField[i][j + myDirectionY] > 2))) {
                                        //only positive direction Y is an obstacle
                                        // move positive x or negative X

                                        double p = random.nextDouble();

                                        if (p < (DRIFT + (1.0 - DRIFT) / 2.0)) {
                                            //positive X direction
                                            space.set(i + myDirectionX, j, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("right random");
                                        } else {
                                            //negative X direction
                                            space.set(i - myDirectionX, j, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("Left random");
                                        }

                                    } else {
                                        // no obstacles
                                        // all three directions possible

                                        double p = random.nextDouble();

                                        if (p > (1.0 - ((1.0 - DRIFT) / 3.0))) {
                                            //negative X direction
                                            space.set(i - myDirectionX, j, previousField[i][j]); // move it left
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("Left random out of all");
                                        } else if (p < (((1.0 - DRIFT) / 3.0) + driftY)) {
                                            //positive Y direction
                                            space.set(i, j + myDirectionY, previousField[i][j]); // move it forward
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("front random out of all");
                                        } else {
                                            //positive X direction
                                            space.set(i + myDirectionX, j, previousField[i][j]); // move it right
                                            space.set(i, j, 0); // set the current space to 0
//                                            System.out.println("right random out of all");
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}