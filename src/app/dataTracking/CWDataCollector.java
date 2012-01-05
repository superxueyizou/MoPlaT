/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package app.dataTracking;

import agent.RVOAgent;
import app.PropertySet;
import app.RVOModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import sim.engine.SimState;
import sim.engine.Steppable;

/**
 *
 * @author vaisaghvt
 */
public class CWDataCollector implements DataTracker {

    private HashMap<RVOAgent, ArrayList<Double>> speedListForAgent;
    private HashMap<RVOAgent, ArrayList<Double>> cumulativeDistanceForAgent;
    private HashMap<RVOAgent, ArrayList<Double>> energySpentByAgent;
    private final RVOModel model;
    private int stepNumber;
    public static final float E_S = 2.23f;
    public static final float E_W = 1.26f;

    public CWDataCollector(RVOModel model, Collection<? extends RVOAgent> agents) {
        stepNumber = 0;
        energySpentByAgent = new HashMap<RVOAgent, ArrayList<Double>>();
        speedListForAgent = new HashMap<RVOAgent, ArrayList<Double>>();
        cumulativeDistanceForAgent = new HashMap<RVOAgent, ArrayList<Double>>();
        this.model = model;
        for (RVOAgent agent : agents) {
            cumulativeDistanceForAgent.put(agent, new ArrayList<Double>());
            speedListForAgent.put(agent, new ArrayList<Double>());
            energySpentByAgent.put(agent, new ArrayList<Double>());
        }
    }

    @Override
    public void step(SimState ss) {
        for (RVOAgent agent : model.getAgentList()) {
            speedListForAgent.get(agent).add(agent.getVelocity().length());


            double distanceInCurrentTimeStep = agent.getVelocity().length()
                    * PropertySet.TIMESTEP;
            double distanceSoFar = 0;
            if (stepNumber > 0) {
                distanceSoFar = cumulativeDistanceForAgent.get(agent).get(stepNumber - 1);
            }
            cumulativeDistanceForAgent.get(agent).add(distanceSoFar + distanceInCurrentTimeStep);

            double energyInCurrentTimeStep = agent.getMass() * (E_S + (E_W * agent.getVelocity().lengthSquared())) * PropertySet.TIMESTEP;
            double energySoFar = 0;
            if (stepNumber > 0) {
                energySoFar = energySpentByAgent.get(agent).get(stepNumber - 1);
            }
            energySpentByAgent.get(agent).add(energySoFar + energyInCurrentTimeStep);
        }
        stepNumber++;
    }

    @Override
    public void storeToFile() {
        String creationTime = "" + System.currentTimeMillis();

        writeToFile(creationTime + "Speed", speedListForAgent);
        writeToFile(creationTime + "Time", cumulativeDistanceForAgent);
        writeToFile(creationTime + "Energy", energySpentByAgent);

    }

    public static void writeToFile(String fileName, HashMap<RVOAgent, ArrayList<Double>> dataForAgent) {
        File file = new File(fileName);
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        for (RVOAgent agent : dataForAgent.keySet()) {
            writer.print("Agent " + agent.getId());
            for (Double speed : dataForAgent.get(agent)) {
                writer.print("\t" + speed);
            }
            writer.println();
        }
        writer.close();
    }
}
