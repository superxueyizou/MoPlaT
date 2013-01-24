/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package app;

import agent.RVOAgent;
import java.util.List;
import sim.engine.SimState;
import sim.engine.Steppable;

/**
 *
 * Checks at the end of each time step if the simulation has ended. And kills the
 * threads if it's done.
 * Change the checks here if you want to change when the simulation ends.
 * @author vaisagh
 */
class WrapUp implements Steppable {

    private final List<RVOAgent> agents;
    private final RVOModel state;

    public WrapUp(RVOModel state, List<RVOAgent> agentList) {
        this.agents = agentList;
        this.state = state;
    }

    @Override
    public void step(SimState arg0) {
        if (PropertySet.LATTICEMODEL) {
            if (!state.getLatticeSpace().isEmpty()) {
             
//                System.out.println(state.getLatticeSpace().getNumberOfAgents());
                return;
            }
        }
        for (RVOAgent agent : agents) {
            if (agent.getCurrentPosition().getX() > 0
                    && agent.getCurrentPosition().getY() > 0
                    && agent.getCurrentPosition().getX() < state.getWorldXSize()
                    && agent.getCurrentPosition().getY() < state.getWorldYSize()) {
                return;
            }
        }
//        System.out.println("here");
        state.kill();
    }
}
