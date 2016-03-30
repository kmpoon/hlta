package tm.hillary

import clustering.PEM

object RunPEM extends App {
  val data = "hillary.txt"
  val options = s"model.bif ${data} ${data} 50 5 0.01  3 ./result/modelname 10 15"
  PEM.main(options.split("\\s+"))
}