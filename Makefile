.PHONY: build stage place schedule alert web clean

build:
	mvn -q compile

stage: build
	mvn -q exec:java -Dexec.mainClass="wethinkcode.stage.StageService"

place: build
	mvn -q exec:java -Dexec.mainClass="wethinkcode.places.PlaceNameService"

schedule: build
	mvn -q exec:java -Dexec.mainClass="wethinkcode.schedule.ScheduleService"

alert: build
	mvn -q exec:java -Dexec.mainClass="wethinkcode.alerts.AlertService"

web: build
	mvn -q exec:java -Dexec.mainClass="wethinkcode.web.WebService"