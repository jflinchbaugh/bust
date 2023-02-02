/** Rotater.java
 *  create a rotater object and create a new image from it rotated to
 * any angle.
 * john m flinchbaugh
 **/
import java.awt.*;
import java.applet.*;
import java.util.*;
import java.io.*;
import java.awt.image.*;

public class Rotater extends Component {
    int pix[];
    int pixout[];
    int w,h;
    double px,py;
    int dw,dh;

    public Rotater(Image img,double x,double y, boolean resize) {
        px=x;
        py=y;
        w=img.getWidth(this);
        h=img.getHeight(this);
        pix=new int[w*h];
        PixelGrabber pg=new PixelGrabber(img,0,0,w,h,pix,0,w);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
            System.err.println(e);
        }

        if (resize) {
            double lx,ly;
            if (px>w/2.0) {
                lx=px;
            } else {
                lx=w-px;
            }
            if (py>h/2.0) {
                ly=py;
            } else {
                ly=h-py;
            }

            dw=(int)(Math.sqrt(lx*lx+ly*ly));
            dh=(int)(Math.sqrt(lx*lx+ly*ly));
        } else {
            dw=w/2;
            dh=h/2;
        }
    }

    public Rotater(Image img,boolean resize,ImageObserver obj) {
        this(img,img.getWidth(obj)/2.0,img.getHeight(obj)/2.0,resize);
    }

    public Image rotate(double theta) {
        Image ret;
        double sin=Math.sin(theta);
        double cos=Math.cos(theta);
        double oneMinusCos=1.0-cos;
        double m00=cos;
        double m01=-sin;
        double m02=px*oneMinusCos+py*sin;
        double m10=sin;
        double m11=cos;
        double m12=py*oneMinusCos-px*sin;

        pixout=new int[4*dw*dh];

        //clear all the pixels
        for (int i=0;i<pixout.length;i++) {
            pixout[i]=0;
        }
        for (int yy=0;yy<h;yy++) {
            for (int xx=0;xx<w;xx++) {
                // find new position
                int x2=(int)(m00*xx+m01*yy+m02);
                int y2=(int)(m10*xx+m11*yy+m12);

                // shift for image size change
                x2+=dw-px;
                y2+=dh-py;

                // test to see if the point lands in range
                if (y2<=0 || x2<=0 ||x2>=(2*dw) || y2>=(2*dh))
                    continue;

                //paint more than one pixel.
                int[] coordinates =
                {
                    (2*dw)*y2+x2
                };
                for (int i=0;i<coordinates.length;i++) {
                    if (coordinates[i]>=pixout.length ||
                        coordinates[i]<0) continue;
                    pixout[coordinates[i]]=pix[w*yy+xx];
                }
            }
        }
        ret=createImage(new MemoryImageSource((2*dw),(2*dh),pixout,
            0,(2*dw)));

		pixout=null;

        return ret;
    }
}
