/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package app.dataTracking;

import agent.RVOAgent;
import app.PropertySet;
import app.RVOModel;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.io.Files;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.vecmath.Point2d;
import javax.vecmath.Tuple2d;
import javax.vecmath.Vector2d;
import org.jfree.chart.JFreeChart;
import sim.engine.SimState;

/**
 *
 * @author vaisaghvt
 */
public class PhysicaDataTracker implements DataTracker {

    private static int numberOfAgents;
    private final RVOModel model;
    private int stepNumber;
    public static final float E_S = 2.23f;
    public static final float E_W = 1.26f;
    public static final String TRACKER_TYPE = "Physica";
    private final static int NUMBER_TO_COLLECT = -1;
//    private final ArrayListMultimap<Integer, Double> energySpentByAgent;
    private final ArrayListMultimap<Integer, Vector2d> velocityListForAgent;
    private final ArrayListMultimap<Integer, Point2d> positionListForAgent;
    private final HashMap<Integer, int[][]> latticeStateForTimeStep;

    public PhysicaDataTracker(RVOModel model, Collection<? extends RVOAgent> agents) {
        stepNumber = 0;
        this.model = model;

//        energySpentByAgent = ArrayListMultimap.create();
        velocityListForAgent = ArrayListMultimap.create();
        positionListForAgent = ArrayListMultimap.create();
        latticeStateForTimeStep = new HashMap<Integer, int[][]>();

        numberOfAgents = model.getAgentList().size();
    }

    @Override
    public void step(SimState ss) {

        for (RVOAgent agent : model.getAgentList()) {

            velocityListForAgent.put(stepNumber, agent.getVelocity());

            positionListForAgent.put(stepNumber, agent.getCurrentPosition());

        }

        if (PropertySet.LATTICEMODEL) {
            latticeStateForTimeStep.put(stepNumber, model.getLatticeSpace().getField());
        }

        stepNumber++;
    }

    @Override
    public String trackerType() {
        return TRACKER_TYPE;
    }

    @Override
    public void storeToFile() {

        String currentFolder = "data"
                + File.separatorChar + this.trackerType()
                + File.separatorChar + model.getScenarioName()
                + File.separatorChar + PropertySet.MODEL
                + File.separatorChar + model.seed()
                + File.separatorChar;

        String testFile = currentFolder + "test";
        try {
            Files.createParentDirs(new File(testFile));
        } catch (IOException ex) {
            Logger.getLogger(PhysicaDataTracker.class.getName()).log(Level.SEVERE, null, ex);
        }



        try {
            writeToFileAgentTuple2dList(currentFolder + model.getScenarioName() + "_" + PropertySet.MODEL + "_" + PropertySet.SEED + "_"
                    + "Velocity", velocityListForAgent);
            writeToFileAgentTuple2dList(currentFolder + model.getScenarioName() + "_" + PropertySet.MODEL + "_" + PropertySet.SEED + "_"
                    + "Position", positionListForAgent);

            if (PropertySet.LATTICEMODEL) {
                writeToFileLatticeState(currentFolder + model.getScenarioName() + "_" + PropertySet.MODEL + "_" + PropertySet.SEED + "_"
                        + "LatticeState", latticeStateForTimeStep);
            }
        } catch (IOException ex) {
            Logger.getLogger(PhysicaDataTracker.class.getName()).log(Level.SEVERE, null, ex);
            assert false;
        }


    }

    @Override
    public boolean hasChart() {
        return false;
    }

    @Override
    public JFreeChart getChart() {
        return null;
    }

    private static <E extends Tuple2d> void writeToFileAgentTuple2dList(String fileName, ArrayListMultimap<Integer, E> dataForAgent) throws IOException {
             System.out.println("Creating "+fileName);
        File fileX = new File(fileName + "_x");
        File fileY = new File(fileName + "_y");
        DataOutputStream writerX = null;
        DataOutputStream writerY = null;
        try {
            writerX = new DataOutputStream(new FileOutputStream(fileX));
            writerY = new DataOutputStream(new FileOutputStream(fileY));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        int writeTime;
        if (NUMBER_TO_COLLECT > 0) {
            writeTime = dataForAgent.keySet().size() / NUMBER_TO_COLLECT;
            //Write integer TimeSteps
            writerX.writeInt(NUMBER_TO_COLLECT);
            writerY.writeInt(NUMBER_TO_COLLECT);
        } else if (NUMBER_TO_COLLECT == -1) {
            writeTime = 1;
            writerX.writeInt(dataForAgent.keySet().size());
            writerY.writeInt(dataForAgent.keySet().size());
        } else {
            assert false;
        }



//write integerNumberOfAgents
        writerX.writeInt(numberOfAgents);
        writerY.writeInt(numberOfAgents);

        for (Integer timeStep : new TreeSet<Integer>(dataForAgent.keySet())) {
            if (timeStep % (writeTime) == 0) {

//                System.out.println(timeStep);


                for (E element : dataForAgent.get(timeStep)) {
                    writerX.writeFloat((float) element.getX());
                    writerY.writeFloat((float) element.getY());

//                    writerX.writeChar('\t');
//                    writerY.writeChar('\t');
                }

//                writerX.writeChar('\n');
//                writerY.writeChar('\n');

            }
        }
        System.out.println("done");
        writerX.close();
        writerY.close();
    }

    private static void writeToFileLatticeState(String fileName, HashMap<Integer, int[][]> latticeStateForTimeStep) throws IOException {
        File file = new File(fileName);
        System.out.println("Creating "+fileName);

        DataOutputStream writer = null;

        try {
            writer = new DataOutputStream(new FileOutputStream(file));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        int writeTime;
        if (NUMBER_TO_COLLECT > 0) {
            writeTime = latticeStateForTimeStep.keySet().size() / NUMBER_TO_COLLECT;
            //Write integer TimeSteps
            writer.writeInt(NUMBER_TO_COLLECT);

        } else if (NUMBER_TO_COLLECT == -1) {
            writeTime = 1;
            writer.writeInt(latticeStateForTimeStep.keySet().size());
        } else {
            assert false;
        }




        //write LATTICE SIZE
        writer.writeInt(latticeStateForTimeStep.get(0).length);
        writer.writeInt(latticeStateForTimeStep.get(0)[0].length);

        for (Integer timeStep : new TreeSet<Integer>(latticeStateForTimeStep.keySet())) {
            if (timeStep % (writeTime) == 0) {
                System.out.println(timeStep);
                int[][] currentState = latticeStateForTimeStep.get(timeStep);
                for (int i = 0; i < currentState.length; i++) {
                    for (int j = 0; j < latticeStateForTimeStep.get(timeStep)[0].length; j++) {
                        writer.writeByte((byte) currentState[i][j]);
//                        writer.writeChar(' ');

                    }
//                    writer.writeChar('\n');
                }


//                writer.writeChar('\n');
//                writer.writeChar('\n');


            }

        }
        System.out.println("done");

        writer.close();

    }
}
