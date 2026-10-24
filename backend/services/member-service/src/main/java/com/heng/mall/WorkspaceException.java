package com.heng.mall;
public class WorkspaceException extends RuntimeException {
 public final int status;
 public WorkspaceException(int status,String message){super(message);this.status=status;}
}
