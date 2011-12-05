/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package motionPlanners.socialforce;

import agent.RVOAgent;
import javax.vecmath.Vector2d;
import motionPlanners.VelocityCalculator;
import sim.util.Bag;
import java.io.*;
import javax.vecmath.Point2d;

/**
 *
 * @author steven
 */
public class ObstacleForce implements VelocityCalculator {
    
    private double[] averageSurroundForce(double Pxi, double Pyi, int N0, double[][] F, double[][] Fx, double[][] Fy) {
        int cellix = Math.mod(Pxi/XX(2),N0)*N0;
        int celliy = Math.mod(Pyi/YY(2),N0)*N0;
        
        int[] neighbouricelly;
        int[] neighbouricellx;
        
        // Check cell boundary condition
        if (cellix==1 && celliy==1){   // Left Top corner
            neighbouricelly = new int[] {2,1,2};
            neighbouricellx = new int[] {1,2,2};
        }
        else if (cellix==1 && celliy== N0){     // Right top corner
            neighbouricelly = new int[] {N0-1,N0-1,N0};
            neighbouricellx = new int[] {1,2,2};
        }
        else if (cellix==N0 && celliy== 1){     // Left bottom corner
            neighbouricelly = new int[] {1,2,2};
            neighbouricellx = new int[] {N0-1,N0,N0-1};
        }
        else if (cellix==N0 && celliy== N0){    // Right bottom corner
            neighbouricelly = new int[] {N0-1,N0-1,N0-1};
            neighbouricellx = new int[] {N0,N0-1,N0};
        }
        else if (cellix==1){    // Top row all column
            neighbouricellx = new int[] {1,1,2,2,2};
            neighbouricelly = new int[] {celliy-1,celliy+1,celliy-1,celliy,celliy+1};
        }
        else if (celliy==N0){   // Right column all row
            neighbouricellx = new int[] {cellix-1,cellix+1,cellix-1,cellix,cellix+1};
            neighbouricelly = new int[] {N0,N0,N0-1,N0-1,N0-1};
        }
        else if (cellix==N0){   // Bottom row all column
            neighbouricellx = new int[] {N0,N0,N0-1,N0-1,N0-1};
            neighbouricelly = new int[] {celliy-1,celliy+1,celliy-1,celliy,celliy+1};
        }
        else if (celliy==1){    // Left column all row
            neighbouricellx = new int[] {cellix-1,cellix+1,cellix-1,cellix,cellix+1};
            neighbouricelly = new int[] {1,1,2,2,2};
        }
        else{   // Otherwise
            neighbouricellx = new int[] {cellix,cellix,cellix+1,cellix+1,cellix+1,cellix-1,cellix-1,cellix-1};
            neighbouricelly = new int[] {celliy-1,celliy+1,celliy-1,celliy,celliy+1,celliy-1,celliy,celliy+1};
        }
        
        // Collecting neighbour Force magnitude and direction for averaging
        int Nneighbour = neighbouricellx.length;
        double[] FAvg = new double[Nneighbour+1];
        double[] FxAvg = new double[Nneighbour+1];
        double[] FyAvg = new double[Nneighbour+1];
        for (int i = 0; i<Nneighbour; i++){
            // Assigning value into buffer
            FAvg[i] = F[neighbouricelly[i]][neighbouricellx[i]];
            FxAvg[i] = Fx[neighbouricelly[i]][neighbouricellx[i]];
            FyAvg[i] = Fy[neighbouricelly[i]][neighbouricellx[i]];
        }
        FAvg[Nneighbour] = F[celliy][cellix];
        FxAvg[Nneighbour] = Fx[celliy][cellix];
        FyAvg[Nneighbour] = Fy[celliy][cellix];
        
        // Average over the force
        double meanFAvg = 0;
        double meanFxAvg = 0;
        double meanFyAvg = 0;
        for (int i = 0; i<Nneighbour+1; i++){
            meanFAvg += FAvg[i];
            meanFxAvg += FxAvg[i];
            meanFyAvg += FyAvg[i];
        }
        meanFAvg = meanFAvg/Nneighbour;
        meanFxAvg = meanFxAvg/Nneighbour;
        meanFyAvg = meanFyAvg/Nneighbour;

        double Fxi = -meanFxAvg*meanFAvg;
        double Fyi = -meanFyAvg*meanFAvg;

        return new double[]{Fxi,Fyi};
    }

    private double[] linearSpaceVector(double a, double b, int c){
        // Create a vector of c elements starting with a, interval difference
        // of intervalValue until b.
        double intervalValue = (b-a)/c;
        double[] resultVector = new double[c];
        
        resultVector[0] = a;
        
        for (int i=0; i<c-1; i++){
            resultVector[i+1] += resultVector[i]+intervalValue;
        }
        
        return resultVector;
    } 
    
    @Override
    public Vector2d calculateVelocity(RVOAgent me, Bag neighbors, Bag obses,
            Vector2d preferredVelocity, double timeStep) {

        double Pxi = tempAgent.getCurrentPosition().getX();
        double Pyi = tempAgent.getCurrentPosition().getY();
        
        int N0=100;
        double Xmin=0;
        double Xmax=10;
        double Ymin=0;
        double Ymax=10;        
        
        double[] x;
        double[] y;
        x = linearSpaceVector(Xmin,Xmax,N0);
        y = linearSpaceVector(Ymin,Ymax,N0);
        
        // Exponential force constant
        double A_wall = 1;             //10  Diameter of the wall
        double B_wall = 1;             //10  Steepness of the wall
        double A_corner = A_wall;      //5 Diameter of the pole
        double B_corner = B_wall;	//5 Steepness of the pole
        
        // The vertex in cartesian coordinate
        double[] vertexx = new double[] {0,0,5,5};
        double[] vertexy = new double[] {-5,0,0,-5};
        
        // Initialize every cell of force
        double[][] F = new double[N0][N0];
        double[][] Fx = new double[N0][N0];
        double[][] Fy = new double[N0][N0];
        for (int i=0; i<N0; i++){
            for (int j=0; j<N0; j++){
                F[j][i] = 0;
                Fx[j][i] = 0;
                Fy[j][i] = 0;
            }
        }
        
        int Nvertex = vertexx.length;  // total number of vertex 
        
        for (int p=0; p<Nvertex; p++){
            // Vector point from vertex p+1 to p
            double bx = vertexx[p+1]-vertexx[p];
            double by = vertexy[p+1]-vertexy[p];
            double normb = Math.sqrt(bx*bx+by*by);
            double bxhat = bx/normb;
            double byhat = by/normb;
            
            for (int i=0; i<N0; i++){
                for (int j=0; j<N0; j++){
                    
                    ////////////////////////////////////////////////////////////
                    // Wall Potential //////////////////////////////////////////
                    ////////////////////////////////////////////////////////////
                    double a = (x[i]-vertexx[p])*bxhat+(y[j]-vertexy[p])*byhat;
                    if (a>=0 && a<=normb){
                        // Vector points on wall's line
                        double wx = a*bxhat + vertexx[p];
                        double wy = a*byhat + vertexy[p];
                        //double normw = Math.sqrt(wx*wx+wy*wy);

                        // Vector point perpendicular to wall's line
                        double rx = x[i]-wx;
                        double ry = y[j]-wy;
                        double normr = Math.sqrt(rx*rx+ry*ry);

                        // Speed magnitude (Exponential potential)
                        double F0 = A_wall*Math.exp(-B_wall*normr);
                        F[j][i] += F0;
                    }
                    
                    ////////////////////////////////////////////////////////////
                    // Half pole at 1st (p) end of wall ////////////////////////
                    ////////////////////////////////////////////////////////////
                    double rx = x[i]-vertexx[p];
                    double ry = y[j]-vertexy[p];
                    double normr = Math.sqrt(rx*rx+ry*ry);

                    double ax = -bx;
                    double ay = -by;
                    double norma = Math.sqrt(ax*ax+ay*ay);

                    // Only calc for half a circle
                    if ((rx*ax+ry*ay)/(normr*norma)>Math.cos(Math.PI/2)){
                        // Speed magnitude (Exponential potential)
                        double F0=A_corner*Math.exp(-B_corner*normr);
                        F[j][i] += F0;
                    }

                    ////////////////////////////////////////////////////////////
                    // Half pole at 2nd (p+1) end of wall //////////////////////
                    ////////////////////////////////////////////////////////////
                    rx = x[i]-vertexx[p+1];
                    ry = y[j]-vertexy[p+1];
                    normr = Math.sqrt(rx*rx+ry*ry);

                    ax = bx;
                    ay = by;
                    norma = Math.sqrt(ax*ax+ay*ay);
                    
                    if ((rx*ax+ry*ay)/(normr*norma)>Math.cos(Math.PI/2)){
                        // Speed magnitude (Exponential potential)
                        double F0=A_corner*Math.exp(-B_corner*normr);
                        F[j][i] += F0;
                    }

                    ////////////////////////////////////////////////////////////
                    // Pole deletion at intersection corner ////////////////////
                    ////////////////////////////////////////////////////////////
                    if ((p>1 && p<Nvertex) || (vertexx[1]==vertexx[Nvertex-1] && vertexy[1]==vertexy[Nvertex-1]) ){
                        rx = x[i]-vertexx[p];
                        ry = y[j]-vertexy[p];
                        normr = Math.sqrt(rx*rx+ry*ry);

                        // Speed magnitude (Exponential potential)
                        double F0=A_corner*Math.exp(-B_corner*normr);
                        F[j][i] -= F0;

                    }
                }
            }
            
        }
        
        
        // Force field direction
        double deltay = y[2] - y[1];
        for (int j=0; j<N0; j++){
            // First row
            Fy[0][j]=(F[1][j]-F[0][j])/deltay;
            // Last row
            Fy[N0-1][j]=(F[N0-1][j]-F[N0-2][j])/deltay;
            // Middle row
            for (int i=1; i<=N0-2; i++){
                Fy[i][j]=(F[i+1][j]-F[i-1][j])/deltay;
            }
        }

        double deltax = x[2] - x[1];
        for (int i=0; i<N0; i++){
            // First column
            Fx[i][0]=(F[i][1]-F[i][0])/deltax;
            // Last column
            Fy[i][N0-1]=(F[i][N0-1]-F[i][N0-2])/deltax;
            // Middle row
            for (int j=1; j<=N0-2; j++){
                // CHCECK WHETHER IS 2 deltax or deltax!!!!!!!
                Fy[i][j]=(F[i][j+1]-F[i][j-1])/deltax;
            }
        }
        
        // Dot product of the force field matrix
        for (int i=0; i<N0; i++){
            for (int j=0; j<N0; i++){
                Fx[i][j] = Fx[i][j]*F[i][j];
                Fy[i][j] = Fy[i][j]*F[i][j];
            }
        }
        
        averageSurroundForce(Pxi, Pyi, N0, F, Fx, Fy);
        

        return Fxi, Fyi;
        
    }
}