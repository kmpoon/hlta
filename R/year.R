titles <- read.csv("aaai_ijcai.20160505.files.csv", sep="\t", quote="")

topics <- read.csv("aaai_ijcai.20160505.TopicsTable.csv", sep="\t", quote="")
topics[,"name"] <- as.character(topics[,"name"])

require(foreign)
assignment <- read.arff("aaai_ijcai.20160505.topics.arff")

build <- function(level) {
    l.topics <- topics[topics$level==level,]
    l.assignment <- assignment[,l.topics$name]
    l.data <- cbind(l.assignment, year=titles$year)
    l.lm <- lm( year ~ ., l.data)
    l.lm
}

## Returns the coefficient of the regression in which the year is
## the response variable and the given topic is the predictor.
find.coefficient <- function(topic, with.conference=FALSE) {
    index <- which(colnames(assignment)==topic)

    data <- cbind(assignment[,index], year=titles$year)
    
    if (with.conference)
        data <- cbind(data, ijcai=(titles$conference == "ijcai"))
    
    colnames(data)[1] <- topic
    m <- lm(year ~ ., as.data.frame(data))

    s <- summary(m)

    if (dim(s$coefficients)[1] < 2) {
        row <- c(0, NA, NA, 1)
        s$coefficients <- rbind(s$coefficients, row)
        rownames(s$coefficients)[2] <- topic
    }

    ## returns the coefficients other than the intercept.
    ## It should have only one coefficient.
    s$coefficients[2,]
}

find.coefficients <- function(topic.names, with.conference=FALSE, order=FALSE) {
    rows <- lapply(topic.names, find.coefficient, with.conference)
    matrix <- do.call(rbind, rows)

    ## use topic names as row names
    rownames(matrix) <- topic.names

    if (order) {
        ## order the rows according to Pr(>|t|)
        matrix[order(matrix[,4]),]
    } else {
        matrix
    }
}

find.coefficient.level <- function(level, with.conference=FALSE, order=TRUE) {
    find.coefficients(topics[topics$level == level,]$name, with.conference, order)
}

## Sorts the coefficients according to Pr(>|t|).
sort <- function(model) {
    s <- summary(model)
    o <- order(s$coefficients[,"Pr(>|t|)"])
    co <- s$coefficients[o[-1],]
    co
}

tabulate.years <- function(topic) {
    z <- topic
    selected <- assignment[,z] > 0.5

    if (sum(selected) == 0) {
        range <- min(titles$year):max(titles$year)
        selected.years <- rep(0, length(range))
        names(selected.years) <- paste(range)
        selected.years
    } else {
        selected.years <- table(titles[selected,]$year)
        selected.years
    }
}

## models <- lapply(seq(5,7), build)
## summaries <- lapply(models, summary)

doc.years <- table(titles$year)

compute.fraction <- function(topic) {
    topic.years <- tabulate.years(topic)
    doc.years <- table(titles$year)

    ## sometimes a topic may not contain documents from all years.
    ## we explicitly set that year to zero in that case.
    if (length(topic.years) != length(doc.years)) {
        years <- topic.years[names(doc.years)]
        years[is.na(years)] <- 0
        names(years) <- names(doc.years)
        topic.years <- years
    }
    
    topic.years / doc.years
}

compute.fraction.matrix <- function(topic) {
    fraction <- compute.fraction(topic)
    m <- cbind(as.integer(rownames(fraction)), fraction)
    colnames(m) <- c("year", "fraction")
    m
}

fit.fraction.lm <- function(topic) {
    m <- compute.fraction.matrix(topic)
    m[,1] <- m[,1] - min(titles$year)
    lm(fraction ~ year, as.data.frame(m))
}

fit.fraction.lm.topics <- function(topic.names) {
    rows <- lapply(topic.names, function(t) { fit.fraction.lm(t)$coefficients })
    matrix <- do.call(rbind, rows)

    ## use topic names as row names
    rownames(matrix) <- topic.names
    matrix
}
    

plot.years <- function(topic) {
    quartz(title=topic, width=5, height=5)
    plot(compute.fraction(topic))
}

plot.fraction.years <- function(topic) {
    quartz(title=topic, width=5, height=5)
    m <- compute.fraction.matrix(topic)

    ## convert to percentage
    m[,2] <- m[,2] * 100
    
    plot(m[,1], m[,2], pch=16, col="blue",
         main=topic, ylab="Documents (%)", xlab="Year", ylim=c(0,50))
    abline(lm(fraction ~ year, as.data.frame(m)))
}

## Computes regression coefficients for topics.  The data is saved if a filename
## is given.
compute.coefficients <- function(filename=NULL, topics.selected=NULL) {
    if (is.null(topics.selected))
        topics.selected = topics$name
    
    coefficients <- find.coefficients(topics.selected)
    lines <- fit.fraction.lm.topics(topics.selected)

    indices <- sapply(rownames(coefficients), function(t) { which(topics$name==t) })
    data <- cbind(topics[indices,], coefficients, lines)

    if (!is.null(filename)) {
        print(filename)
        write.table(data, file=filename, quote=FALSE, sep="\t", row.names=FALSE)
    }

    data
}
