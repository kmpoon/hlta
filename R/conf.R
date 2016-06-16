conf.data <- read.csv("aaai_ijcai.20160505.conference.csv", sep="\t", quote="")

plot.conf <- function(topic) {
    index <- which(conf.data$name == topic)
    values <- c(conf.data$fraction..aaai[index], conf.data$fraction..ijcai[index]) *
        100
    names(values) <- c("AAAI", "IJCAI")

    filename <- paste(topic, ".conf.pdf", sep="")
    quartz(topic, width=5, height=3)
    barplot(values, xlab="Documents (%)", horiz=TRUE)
    quartz.save(filename, type="pdf")
}

