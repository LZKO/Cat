package test;

public class Employee extends Human {
    private static int COUNT; // 雇员总人数
    private String workNo; // 工号
    private int salary; // 工资

    public static int getCOUNT() {
        return COUNT;
    }

    public static void setCOUNT(int COUNT) {
        Employee.COUNT = COUNT;
    }

    public String getWorkNo() {
        return workNo;
    }

    public void setWorkNo(String workNo) {
        this.workNo = workNo;
    }

    public int getSalary() {
        return salary;
    }

    public void setSalary(int salary) {
        this.salary = salary;
    }

    @Override
    public String toString() {
        return super.toString()+"Employee{" +
                "workNo='" + workNo + '\'' +
                ", salary=" + salary +
                '}';
    }
}
