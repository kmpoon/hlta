
conf.data <- read.csv("aaai_ijcai.20160505.conference.csv", sep="\t", quote="")

plot.conf <- function(topic) {
    index <- which(conf.data$name == topic)
    values <- c(conf.data$fraction..aaai[index], conf.data$fraction..ijcai[index]) *
        100
    names(values) <- c("AAAI", "IJCAI")

    filename <- paste(topic, ".conf.pdf", sep="")
    quartz(topic, width=5, height=3)
    barplot(values, xlab="Documents (%)", horiz=TRUE, col=c("red","blue"))
    quartz.save(filename, type="pdf")
}

topics.l7 <- as.character(conf.data[conf.data$level==7,]$name)

plot.conf.multiple <- function(topics, order=TRUE) {
    values <- conf.data[topics,]$phi..ijcai
    names(values) <- conf.data[topics,]$words

    if (order) {
        values <- values[order(values)]
    }

    ii <- cut(values, breaks = seq(min(values), max(values), len = 20), 
          include.lowest = TRUE)
    ## Use bin indices, ii, to select color from vector of n-1 equally spaced colors
    colors <- colorRampPalette(c("red", "blue"))(20)[ii]
    
    ## col <- colorRampPalette(c("red", "blue"))
    ## colors <- colvalues
    quartz(width=8,height=5)
    opar <- par(no.readonly=TRUE)
    par(mar=c(3.5,1,0.1,30), cex.axis=0.75)
    mp <- barplot(values, horiz=TRUE,
                  xlim=c(-0.2,0.2), space=1,yaxt="n",
                  col=colors)
    axis(4, at=mp, labels=names(values), las=2, tick=FALSE)
    mtext("IJCAI", side=1, line=2, adj=1, col="blue")
    mtext("AAAI", side=1, line=2, adj=0, col="red")
    par(opar)
}

plot.conf.separate <- function(topics) {
    for (t in topics) {
        plot.conf(t)
    }
}
