package tm.util

object LinearRegression {
  
  def main(args: Array[String]):Unit = {
    LinearRegression(List((1.3, 2.2),(2.1, 5.8),(3.7, 10.2),(4.2, 11.8)))
  }
  /**
   * See https://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html
   */
  def apply(points: Seq[(Double, Double)]) = { 
    val n = points.size;

    // first pass: read in data, compute xbar and ybar
    var sumx = 0.0
    var sumy = 0.0
    var sumx2 = 0.0
    points.foreach{case (x, y) =>
      sumx  += x
      sumx2 += x * x
      sumy  += y
    }
    val xbar = sumx / n;
    val ybar = sumy / n;

    // second pass: compute summary statistics
    var xxbar = 0.0
    var yybar = 0.0
    var xybar = 0.0
    points.foreach{ case (x, y) =>
      xxbar += (x - xbar) * (x - xbar)
      yybar += (y - ybar) * (y - ybar)
      xybar += (x - xbar) * (y - ybar)
    }
    val beta1 = xybar / xxbar;
    val beta0 = ybar - beta1 * xbar;

    // print results
    //println("y   = " + beta1 + " * x + " + beta0);

    // analyze results
    val df = n - 2;
    var rss = 0.0;      // residual sum of squares
    var ssr = 0.0;      // regression sum of squares
    points.foreach{ case (x, y) =>
      val fit = beta1*x + beta0;
      rss += (fit - y) * (fit - y)
      ssr += (fit - ybar) * (fit - ybar)
    }
    
    val R2 = ssr / yybar;
    //println("R^2  = " + R2);

    //println("SSTO = " + yybar);
    //println("SSE  = " + rss);
    //println("SSR  = " + ssr);
    
    (beta1, beta0, R2, yybar, rss, ssr)
  }
  
}