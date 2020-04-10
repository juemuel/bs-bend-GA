package GeneticAlgorithm;

import Main.Controller;
import MapObjects.Customer;
import MapObjects.Depot;
import MapObjects.Vehicle;
import Utils.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Individual in Population 种群中的一个个体（染色体即路线）
 * Contains Routes from all Vehicles 包括所有车辆的路线
 */
public class Individual {
    private List<Depot> depots;// 几个仓库
    private List<Vehicle> vehicles; // 仓库对应的车
    private int durationPenaltyRate; // 持续惩罚率
    private int loadPenaltyRate; // 负荷惩罚率
    private double fitness; // 评价值
    private boolean isValid; // 有效判断

    /**
     * Generates initialSolution and calculates distances       Individual构造函数生成初始结果并计算距离
     *
     * @param depots
     * @param durationPenaltyRate
     * @param loadPenaltyRate
     */
    public Individual(List<Depot> depots, int durationPenaltyRate, int loadPenaltyRate) {
        this.depots = depots;
        this.durationPenaltyRate = durationPenaltyRate;
        this.loadPenaltyRate = loadPenaltyRate;
        vehicles = new ArrayList<>();
        calculateFitness();
    }

    public Individual(List<Depot> depots, int durationPenaltyRate, int loadPenaltyRate, List<Vehicle> vehicles) {
        this.depots = depots;
        this.durationPenaltyRate = durationPenaltyRate;
        this.loadPenaltyRate = loadPenaltyRate;
        this.vehicles = new ArrayList<>(vehicles);
        calculateFitness();
    }

    public boolean generateOptimizedIndividual(boolean force) { //精英选择1
        for (Depot depot : depots) {
            List<Vehicle> depotVehicles = createDepotVehicles(depot);

            List<Customer> depotCustomers = new ArrayList<>(depot.getCustomers()); // Current depot's customers
            Collections.shuffle(depotCustomers);

            for (Customer customer : depotCustomers) {
                boolean customerAdded = false;
                int triesLeft = 100;
                while (!customerAdded && triesLeft > 0) {

                    double minDuration = Double.MAX_VALUE;
                    int minRouteIndex = -1;
                    Vehicle minVehicle = null;

                    Collections.shuffle(depotVehicles);
                    for (Vehicle vehicle : depotVehicles) {
                        double tempMinDuration = Double.MAX_VALUE;
                        int tempRouteIndex = -1;

                        if (vehicle.getCurrentLoad() + customer.getLoadDemand() <= depot.getMaxLoad()) {
                            if (vehicle.getRoute().size() == 0) {
                                tempMinDuration = vehicle.calculateRouteDurationIfCustomerAdded(0, customer);
                                tempRouteIndex = 0;
                            } else {
                                for (int i = 0; i < vehicle.getRoute().size(); i++) {
                                    double tempDuration = vehicle.calculateRouteDurationIfCustomerAdded(i, customer);

                                    if (tempDuration < tempMinDuration) {
                                        tempMinDuration = tempDuration;
                                        tempRouteIndex = i;
                                    }
                                }
                            }
                        }

                        if (tempMinDuration < minDuration) {
                            minDuration = tempMinDuration;
                            minRouteIndex = tempRouteIndex;
                            minVehicle = vehicle;
                        }
                    }

                    if (minVehicle == null) {
                        triesLeft--;
                    } else {
                        minVehicle.addCustomerToRoute(minRouteIndex, customer);

                        if (depot.getMaxDuration() != 0.0 && minVehicle.calculateRouteDuration() > depot.getMaxDuration() && !force) {
                            minVehicle.removeCustomerFromRoute(customer);
                            triesLeft--;
                        } else {
                            customerAdded = true;
                        }
                    }
                }
                if (triesLeft == 0 && !force) { // Giving up generating this initial isValid
                    return false;
                }
            }
            this.vehicles.addAll(depotVehicles);
        }

        return true;
    }

    public boolean generateOptimizedIndividual2(boolean force) {  //精英选择2
        for (Depot depot : depots) {
            List<Vehicle> depotVehicles = createDepotVehicles(depot);
            List<Customer> depotCustomers = depot.getCustomers(); // Current depot's customers
            Collections.shuffle(depotCustomers);

            int triesLeft = 100;
            for (Customer customer : depotCustomers) { // Assign customer to random vehicle
                boolean customerAdded = false;
                while (!customerAdded && triesLeft > 0) {
                    int randomIndex = Utils.randomIndex(depotVehicles.size()); // Random vehicle index
                    Vehicle randomVehicle = depotVehicles.get(randomIndex);

                    customerAdded = randomVehicle.smartAddCustomerToRoute(customer, force);

                    if (!customerAdded) {
                        triesLeft--;
                    }
                }

                if (triesLeft == 0) { // Giving up generating this initial isValid
                    return false;
                }
            }

            this.vehicles.addAll(depotVehicles);
        }

        return true;
    }

    public boolean generateRandomIndividual() { //随机选择1（灾变）
        for (Depot depot : depots) {
            List<Vehicle> depotVehicles = createDepotVehicles(depot);
            List<Customer> depotCustomers = depot.getCustomers(); // Current depot's customers
            Collections.shuffle(depotCustomers);

            for (Customer customer : depotCustomers) { // Assign customer to random vehicle
                boolean customerAdded = false;
                int randomIndex = Utils.randomIndex(depotVehicles.size()); // Random vehicle index
                Vehicle randomVehicle = depotVehicles.get(randomIndex);

                while (!customerAdded) {
                    customerAdded = randomVehicle.addCustomerToRoute(customer);
                }
            }

            this.vehicles.addAll(depotVehicles);
        }
        return true;
    }

    /**
     * Performs n random mutations on the vehicles based on the mutationRate
     * Mutation is only executed if random < crossOverRate
     */
    public List<Vehicle> swapMutation() {//变异函数1
        // Copy of vehicles
        List<Vehicle> newVehicles = deepCopyVehicles();

        int randomIndex = Utils.randomIndex(vehicles.size());
        Vehicle vehicle = vehicles.get(randomIndex);
        List<Customer> newRoute = vehicle.swapMutate();
        Vehicle newVehicle = new Vehicle(vehicle.getStartDepot(), newRoute);

        newVehicles.remove(vehicle);
        newVehicles.add(newVehicle);

        return newVehicles;
    }

    public List<Vehicle> swapMutation2() {//变异函数2
        // Copy of vehicles
        List<Vehicle> newVehicles = deepCopyVehicles();

        int randomIndex1 = Utils.randomIndex(newVehicles.size());
        int randomIndex2 = Utils.randomIndex(newVehicles.size());
        Vehicle vehicle1 = newVehicles.get(randomIndex1);
        Vehicle vehicle2 = newVehicles.get(randomIndex2);
        randomIndex1 = Utils.randomIndex(vehicle1.getRoute().size());
        randomIndex2 = Utils.randomIndex(vehicle2.getRoute().size());

        Customer customer1 = null;
        Customer customer2 = null;

        if (vehicle1.getRoute().size() != 0) {
            randomIndex1 = Utils.randomIndex(vehicle1.getRoute().size());
            customer1 = vehicle1.getRoute().get(randomIndex1);
        }

        if (vehicle2.getRoute().size() != 0) {
            randomIndex2 = Utils.randomIndex(vehicle2.getRoute().size());
            customer2 = vehicle2.getRoute().get(randomIndex2);
        }

        if (customer1 != null) {
            vehicle1.removeCustomerFromRoute(customer1);
            vehicle2.addCustomerToRoute(randomIndex2, customer1);
        }

        if (customer2 != null) {
            vehicle2.removeCustomerFromRoute(customer2);
            vehicle1.addCustomerToRoute(randomIndex1, customer2);
        }

        return newVehicles;
    }

    public List<Vehicle> crossMutation() { //交叉变异
        // Copy of vehicles
        List<Vehicle> newVehicles = deepCopyVehicles();

        // Pick two random vehicles
        int randomIndex1 = Utils.randomIndex(vehicles.size());
        int randomIndex2 = Utils.randomIndex(vehicles.size());

        Vehicle randomVehicle1 = vehicles.get(randomIndex1).clone();
        Vehicle randomVehicle2 = vehicles.get(randomIndex2).clone();

        // Remove the old vehicles
        newVehicles.remove(randomVehicle1);
        newVehicles.remove(randomVehicle2);

        // Mutate the two vehicles and add them to newVehicles
        List<Customer>[] mutatedRoutes = randomVehicle1.crossMutate(randomVehicle2.getRoute());
        newVehicles.add(new Vehicle(randomVehicle1.getStartDepot(), randomVehicle2.getEndDepot(), mutatedRoutes[0]));
        newVehicles.add(new Vehicle(randomVehicle2.getStartDepot(), randomVehicle1.getEndDepot(), mutatedRoutes[1]));
        return newVehicles;
    }

    private List<Vehicle> createDepotVehicles(Depot depot) { //生成部门原有车辆
        List<Vehicle> vehicles = new ArrayList<>();

        for (int i = 0; i < depot.getMaxVehicles(); i++) {
            Vehicle v = new Vehicle(depot);
            vehicles.add(v);
        }
        return vehicles;
    }

    public void calculateFitness() { //个体评价值计算函数
        double calculatedFitness = 0.0;
        isValid = true;

        for (Vehicle vehicle : vehicles) {
            double penalty = 0;

            double maxDuration = vehicle.getStartDepot().getMaxDuration();
            double duration = vehicle.calculateRouteDuration();
            if (maxDuration != 0 && duration > maxDuration) {
                penalty += ((duration - maxDuration) * durationPenaltyRate);
            }

            double maxLoad = vehicle.getStartDepot().getMaxLoad();
            double load = vehicle.getCurrentLoad();
            if (maxLoad != 0 && load > maxLoad) {
                penalty += ((load - maxLoad) * loadPenaltyRate);
            }

            if (penalty > 0) {
                isValid = false;
            }

            calculatedFitness += (duration + penalty);
        }

        this.fitness = calculatedFitness;
    }

    public double getFitness() { //获取个体的评价值
        return fitness;
    }

    public double getDuration() { // 获取个体的持续时间
        double duration = 0.0;

        for (Vehicle vehicle : vehicles) {
            duration += vehicle.calculateRouteDuration();
        }

        return duration;
    }

    public List<Vehicle> getVehicles() { // 获取个体的汽车清单
        return vehicles;
    }

    /**
     * @param otherRoute
     * @return
     */

    public List<Vehicle> singlePointCrossOver(List<Customer> otherRoute) {
        if (vehicles == null) {
            throw new NullPointerException("No vehicles in solution");
        } else if (otherRoute.size() == 0) {
            return vehicles;
        }

        List<Vehicle> newVehicles = deepCopyVehicles();

        // Remove route from otherRoute
        removeRouteFromVehicles(newVehicles, otherRoute);

        // Rull gjennom alle ruter og regn ut diff i fitness på alle mulige steder
        double minFitnessIfAdded = Double.MAX_VALUE;
        Vehicle minVehicle = null;
        int minIndex = -1;

        for (Vehicle vehicle : newVehicles) {
            double fitnessIfAdded;

            if (vehicle.getRoute().size() == 0) {
                fitnessIfAdded = calculateFitnessIfRouteAdded(newVehicles, vehicle, 0, otherRoute);

                minFitnessIfAdded = fitnessIfAdded;
                minVehicle = vehicle;
                minIndex = 0;
            } else {
                for (int routeIndex = 0; routeIndex < vehicle.getRoute().size(); routeIndex++) {
                    fitnessIfAdded = calculateFitnessIfRouteAdded(newVehicles, vehicle, routeIndex, otherRoute);

                    if (fitnessIfAdded < minFitnessIfAdded) {
                        minFitnessIfAdded = fitnessIfAdded;
                        minVehicle = vehicle;
                        minIndex = routeIndex;
                    }
                }
            }

            setBestEndDepot(vehicle);
        }

        if (minVehicle == null) {
            throw new Error("MinVehicle is null");
        } else {
            minVehicle.addOtherRouteToRoute(minIndex, otherRoute);
        }

        return newVehicles;
    }

    private List<Vehicle> deepCopyVehicles() {
        // Creating a deep copy of vehicles
        List<Vehicle> newVehicles = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            newVehicles.add(vehicle.clone());
        }
        return newVehicles;
    }

    private void removeRouteFromVehicles(List<Vehicle> newVehicles, List<Customer> routeToRemove) { // 去除路径
        for (Vehicle vehicle : newVehicles) {
            List<Customer> routeCopy = new ArrayList<>(vehicle.getRoute()); // Copy to avoid error when removing while looping
            for (Customer customer : routeCopy) {
                if (routeToRemove.contains(customer)) {
                    vehicle.removeCustomerFromRoute(customer);
                }
            }
        }
    }

    //设置最佳的结束仓库
    private void setBestEndDepot(Vehicle vehicle) {
        if (vehicle.getRoute().size() > 0) {
            double currentMinDistance = Double.MAX_VALUE;
            Depot currentBestEndDepot = null;
            for (Depot depot : depots) {
                double distance = depot.distance(vehicle.getRoute().get(vehicle.getRoute().size() - 1));
                if (distance < currentMinDistance) {
                    currentMinDistance = distance;
                    currentBestEndDepot = depot;
                }
            }
            vehicle.setEndDepot(currentBestEndDepot);
        }
    }

    private double calculateFitnessIfRouteAdded(List<Vehicle> vehicles, Vehicle vehicle, int addIndex, List<Customer> routeToAdd) {
        List<Vehicle> originalVehiclesCopy = this.vehicles;
        double originalFitness = fitness;
        this.vehicles = vehicles;
        vehicle.addOtherRouteToRoute(addIndex, routeToAdd);
        calculateFitness();
        double newFitness = fitness;
        vehicle.removeRouteFromRoute(routeToAdd);
        this.fitness = originalFitness;
        this.vehicles = originalVehiclesCopy;
        return newFitness;
    }

    private double calculateFitnessIfCustomerAdded(List<Vehicle> vehicles, Vehicle vehicle, int addIndex, Customer customerToAdd) {
        List<Vehicle> originalVehiclesCopy = this.vehicles;
        double originalFitness = fitness;
        this.vehicles = vehicles;
        vehicle.addCustomerToRoute(addIndex, customerToAdd);
        calculateFitness();
        double newFitness = fitness;
        vehicle.removeCustomerFromRoute(customerToAdd);
        this.fitness = originalFitness;
        this.vehicles = originalVehiclesCopy;
        return newFitness;
    }

    public boolean isValid() {
        return isValid;
    }

    public void saveToFile() throws IOException { //保存文件（包括总评价值，每个仓库素有车辆的使用情况与路径）
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        BufferedWriter writer = new BufferedWriter(new FileWriter("solution-" + Controller.fileName + "-" + timestamp.getTime()));

        writer.write(Double.toString(Utils.round(getDuration(), 2)));

        int vehicleId = 0;
        String lastDepotId = vehicles.get(0).getStartDepot().getId();

        for (Vehicle vehicle : vehicles) {
            if (vehicle.getStartDepot().getId() == lastDepotId) {
                vehicleId++;
                writer.newLine();
                writer.write(vehicle.getStartDepot().getId() + "\t" // s: number of the start depot
                        + vehicleId + "\t" // k: number of the vehicle (for above depot)
                        + Utils.round(vehicle.calculateRouteDuration(), 2) + "   " // d: duration of the route for a particular vehicle from a particular depot
                        + vehicle.getCurrentLoad() + "\t" // q: carried load of the vehicle
                        + vehicle.getEndDepot().getId() + "\t" // e: number of the end depot
                        + vehicle.getRoute().toString() // list: ordered sequence of customers (served by a particular vehicle)
                        .replace(",", "")  // remove the commas
                        .replace("[", "")  // remove the right bracket
                        .replace("]", "")  // remove the left bracket
                );
            } else {
                vehicleId = 1;
                writer.newLine();
                writer.write(vehicle.getStartDepot().getId() + "\t" // s: number of the start depot 输出仓库编号
                        + vehicleId + "\t" // k: number of the vehicle (for above depot) 输出仓库中当前车辆对应的编号
                        + Utils.round(vehicle.calculateRouteDuration(), 2) + "   " // d: duration of the route for a particular vehicle from a particular depot 输出该车辆行驶路线的时间
                        + vehicle.getCurrentLoad() + "\t" // q: carried load of the vehicle 输出当前车辆的实际载重
                        + vehicle.getEndDepot().getId() + "\t" // e: number of the end depot 输出起始仓库号
                        + vehicle.getRoute().toString() // list: ordered sequence of customers (served by a particular vehicle) 输出该路径的客户路线
                        .replace(",", "")  // remove the commas
                        .replace("[", "")  // remove the right bracket
                        .replace("]", "")  // remove the left bracket
                );
            }
            lastDepotId = vehicle.getStartDepot().getId();
        }

        writer.close();
    }

    public void appendToCSV(int populationSize, double crossOverRate, double mutationRate, int k, List<Double> generations) throws IOException { // 将当前结果追加到csv表中
        File file = new File("results.csv");

        if (!file.exists()) {
            file.createNewFile();
            createCsv();
        }

        FileWriter csvWriter = new FileWriter("results.csv", true);
        csvWriter.append("\n")
                .append(Controller.fileName)
                .append(", ")
                .append(String.valueOf(populationSize))
                .append(", ")
                .append(String.valueOf(crossOverRate))
                .append(", ")
                .append(String.valueOf(mutationRate))
                .append(", ")
                .append(String.valueOf(k));

        for (int i = 0; i < 10000; i++) {
            if (i > generations.size() - 1) {
                csvWriter.append(", ").append(String.valueOf(generations.get(generations.size() - 1)));
            } else {
                csvWriter.append(", ").append(String.valueOf(generations.get(i)));
            }
        }
        csvWriter.close();
    }

    private void createCsv() throws IOException { // 创建csv表用于存储结果（map名，种群规模，交叉因子，突变率，k，各种群的速率）
        FileWriter csvWriter = new FileWriter("results.csv");
        csvWriter.append("Map, Population size, Crossover rate, Mutation rate, k");

        for (int i = 1; i <= 10000; i++) {
            csvWriter.append(", ").append(String.valueOf(i));
        }
        csvWriter.close();
    }

}
