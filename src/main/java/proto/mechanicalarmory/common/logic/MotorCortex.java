package proto.mechanicalarmory.common.logic;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.joml.Vector3d;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

public class MotorCortex implements INBTSerializable<ListTag> {

    private final float armSize;
    private final InteractionType interactionType;
    float[][] rotation = new float[3][3];
    float[][] animationRotation = new float[3][3];
    ArmEntity te;
    private final static float PI = (float) Math.PI;

    public MotorCortex(ArmEntity tileArmBase, float armSize, InteractionType interactionType) {
        te = tileArmBase;
        this.armSize = armSize;
        this.interactionType = interactionType;
    }

    public ActionResult move(Vector3d target, Direction facing) {
        Vector3d combinedVec = target;
        float pitch = (float) Math.atan2(combinedVec.y, Math.sqrt(combinedVec.x * combinedVec.x + combinedVec.z * combinedVec.z));
        pitch -= (float) (Math.PI/2);
        //model initial angle is -90 degrees
        float yaw = (float) Math.atan2(-combinedVec.z, combinedVec.x);
        yaw -= (float) (Math.PI/2);

        float dist = (float) combinedVec.length();

        float extraPitchArc = (float) Math.acos(dist / armSize / 2);
        if (Double.isNaN(extraPitchArc)) {
            extraPitchArc = 0;
        }
        float armArcTarget = (float) (Math.asin(dist / armSize / 2) * 2 - PI);
        if (Double.isNaN(armArcTarget)) {
            armArcTarget = 0;
        }

        pitch = pitch + extraPitchArc;

        boolean distReached = false;

        animationRotation[1][0] = rotation[1][0];
        rotation[1][0] = (rotateShortest(rotation[1][0], 0.3141595f, armArcTarget));
        if (rotation[1][0] == armArcTarget) {
            if (extraPitchArc != 0) {
                distReached = true;
            } else {
                distReached = dist <= 2 * armSize + 0.5;
            }
        }

        animationRotation[0][0] = rotation[0][0];
        rotation[0][0] = rotateShortest(rotation[0][0], 0.3141595f, pitch);
        boolean pitchReached = rotation[0][0] == pitch;

        animationRotation[0][1] = rotation[0][1];
        rotation[0][1] = rotateShortest(rotation[0][1], 0.3141595f, yaw);
        float yawDiff = (Math.abs(rotation[0][1]) - Math.abs(animationRotation[0][1]));
        boolean yawReached = (yawDiff < 0.01 && yawDiff > -0.01);

        animationRotation[2][0] = rotation[2][0];
        animationRotation[2][1] = rotation[2][1];
        //rotation[2][0] = -PI / 2 - rotation[1][0] - rotation[0][0];

        if (yawReached && pitchReached && distReached) {
//            float targetHandYaw = (PI - rotation[0][1]) % PI;
//            float targetHandPitch = (-rotation[1][0] - rotation[0][0]) % PI;
//
//            EnumFacing accessingSide = facing.getOpposite();
//
//            if (accessingSide == UP) {
//                targetHandYaw = 0;
//                targetHandPitch = +PI / 2 - rotation[1][0] - rotation[0][0];
//            } else if (accessingSide == DOWN) {
//                targetHandYaw = 0;
//                targetHandPitch = -PI / 2 - rotation[1][0] - rotation[0][0];
//            } else if (armPoint.x > target.x && armPoint.z < target.z) {
//                //arm is NE from target
//                if (accessingSide == SOUTH) {
//                    targetHandYaw = targetHandYaw - PI / 2;
//                } else if (accessingSide == NORTH) {
//                    targetHandPitch = targetHandPitch + PI;
//                    targetHandYaw = -targetHandYaw + PI / 2;
//                } else if (accessingSide == EAST) {
//                    targetHandPitch = targetHandPitch - PI;
//                    targetHandYaw = -targetHandYaw + PI;
//                } else {
//                    targetHandYaw = targetHandYaw + PI;
//                }
//            } else if (armPoint.x < target.x && armPoint.z < target.z) {
//                //arm is NW from target
//                if (accessingSide == SOUTH) {
//                    targetHandYaw = targetHandYaw - PI / 2;
//                } else if (accessingSide == NORTH) {
//                    targetHandPitch = targetHandPitch + PI;
//                    targetHandYaw = -targetHandYaw + PI / 2;
//                } else if (accessingSide == WEST) {
//                    targetHandPitch = targetHandPitch - PI;
//                    targetHandYaw = -targetHandYaw;
//                }
//            } else if (armPoint.x < target.x && armPoint.z > target.z) {
//                //arm is SW from target
//                if (accessingSide == NORTH) {
//                    targetHandYaw = targetHandYaw - PI / 2;
//                } else if (accessingSide == SOUTH) {
//                    targetHandPitch = targetHandPitch - PI;
//                    targetHandYaw = targetHandYaw + PI;
//                } else if (accessingSide == WEST) {
//                    targetHandPitch = targetHandPitch - PI;
//                    targetHandYaw = -targetHandYaw - PI;
//                } else {
//                    targetHandYaw = targetHandYaw + PI;
//                }
//            } else if (armPoint.x > target.x && armPoint.z > target.z) {
//                //arm is SE from target
//                if (accessingSide == NORTH) {
//                    targetHandYaw = targetHandYaw - PI / 2;
//                } else if (accessingSide == EAST) {
//                    targetHandPitch = targetHandPitch - PI;
//                    targetHandYaw = -targetHandYaw;
//                } else if (accessingSide == SOUTH) {
//                    targetHandYaw = -targetHandYaw + PI / 2;
//                    targetHandPitch = targetHandPitch - PI;
//                }
//            } else if (armPoint.x == target.x) {
//                if (armPoint.z > target.z) {
//                    if (accessingSide == EAST) {
//                        targetHandYaw = -targetHandYaw;
//                    } else if (accessingSide == SOUTH) {
//                        targetHandPitch = targetHandPitch + PI;
//                        targetHandYaw = 0;
//                    } else if (accessingSide == NORTH) {
//                        targetHandYaw = 0;
//                    }
//                } else {
//                    if (accessingSide == WEST) {
//                        targetHandYaw = -targetHandYaw;
//                    } else if (accessingSide == NORTH) {
//                        targetHandPitch = targetHandPitch + PI;
//                        targetHandYaw = 0;
//                    } else if (accessingSide == SOUTH) {
//                        targetHandYaw = 0;
//                    }
//                }
//            } else if (armPoint.z == target.z) {
//                if (armPoint.x > target.x) {
//                    if (accessingSide == SOUTH) {
//                        targetHandYaw = targetHandYaw + PI / 2;
//                    } else if (accessingSide == NORTH) {
//                        targetHandYaw = targetHandYaw - PI / 2;
//                    } else if (accessingSide == EAST) {
//                        targetHandPitch = targetHandPitch - PI;
//                    }
//                } else {
//                    if (accessingSide == NORTH) {
//                        targetHandYaw = targetHandYaw - PI / 2;
//                    } else if (accessingSide == SOUTH) {
//                        targetHandYaw = targetHandYaw + PI / 2;
//                    } else if (accessingSide == WEST) {
//                        targetHandPitch = targetHandPitch - PI;
//                    }
//                }
//            }
//
//            rotation[2][1] = rotateShortest(rotation[2][1], 0.10F, targetHandYaw);
//            float yawDiffHand = (Math.abs(rotation[2][1]) - Math.abs(animationRotation[2][1]));
//            boolean yawHandReached = (yawDiffHand < 0.01 && yawDiffHand > -0.01);
//
//            rotation[2][0] = rotateShortest(rotation[2][0], 0.10F, targetHandPitch);
//            float pitchDiffHand = (Math.abs(rotation[2][0]) - Math.abs(animationRotation[2][0]));
//            boolean pitchHandReached = (pitchDiffHand < 0.01 && pitchDiffHand > -0.01);
//
//            if (pitchHandReached && yawHandReached) {
//                return ActionResult.SUCCESS;
//            }
            return ActionResult.SUCCESS;
        } else {
            float targetHandYaw = 0;
            float targetHandPitch = -PI / 2 - rotation[1][0] - rotation[0][0] + 0.01F;
            rotation[2][1] = rotateShortest(rotation[2][1], 0.15F, targetHandYaw);
            rotation[2][0] = rotateToReach(rotation[2][0], 0.15F, targetHandPitch);
        }
        return ActionResult.CONTINUE;
    }

    public float[] getRotation(int idx) {
        return rotation[idx];
    }

    float rotateShortest(float currentRotation, float angularSpeed, float targetRotation) {
        currentRotation = currentRotation % (2 * PI);

        float shortestAngle = (float) (((targetRotation - currentRotation) + 3 * Math.PI) % (2 * Math.PI) - Math.PI);

        if (shortestAngle >= 0.1F) {
            float result = currentRotation + Math.min(angularSpeed, shortestAngle);
            return result % (2 * PI);
        } else if (shortestAngle <= -0.1F) {
            float result = currentRotation + Math.max(-angularSpeed, shortestAngle);
            return result % (2 * PI);
        } else {
            return (currentRotation + shortestAngle) % (2 * PI);
        }
    }

    float rotateToReach(float currentRotation, float angularSpeed, float targetedRotation) {
        float diff = targetedRotation - currentRotation;
        if (diff <= -0.1) {
            return currentRotation - angularSpeed;
        } else if (diff >= 0.1) {
            return currentRotation + angularSpeed;
        } else if (diff > -0.1 && diff < 0.1) {
            return targetedRotation;
        }
        return currentRotation;
    }

    public void rest() {
        if (rotation[0][1] >= 0.099F) {
            rotation[0][1] = rotation[0][1] - 0.1F;
        } else if (rotation[0][1] <= -0.099F) {
            rotation[0][1] = rotation[0][1] + 0.1F;
        } else {
            rotation[0][1] = 0;
        }

        if (rotation[0][0] >= 0.099F) {
            rotation[0][0] = rotation[0][0] - 0.1F;
        } else if (rotation[0][0] <= -0.099F) {
            rotation[0][0] = rotation[0][0] + 0.1F;
        } else {
            rotation[0][0] = 0;
        }

        if (rotation[1][0] >= 0.099F) {
            rotation[1][0] = rotation[1][0] - 0.1F;
        } else if (rotation[0][0] <= -0.099F) {
            rotation[1][0] = rotation[1][0] + 0.1F;
        } else {
            rotation[1][0] = 0;
        }

        if (rotation[2][0] >= 0.099F) {
            rotation[2][0] = rotation[2][0] - 0.1F;
        } else if (rotation[0][0] <= -0.099F) {
            rotation[2][0] = rotation[2][0] + 0.1F;
        } else {
            rotation[2][0] = 0;
        }
    }

    public float[] getAnimationRotation(int idx) {
        return animationRotation[idx];
    }

    @Override
    public ListTag serializeNBT(HolderLookup.Provider provider) {
        ListTag tagList = new ListTag();
        for (int i = 0; i < 3; i++) {
            for (float r : getRotation(i)) {
                tagList.add(FloatTag.valueOf(r));
            }
        }
        return tagList;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, ListTag tagList) {
        int axis = 0;
        int coords = 0;
        for (int i = 0; i < tagList.size(); i++) {
            if (coords > 2) {
                axis += 1;
                coords = 0;
            }
            getRotation(axis)[coords] = tagList.getFloat(i);
            coords++;
        }

        for (int i = 0; i < rotation.length; i++) {
            System.arraycopy(rotation[i], 0, animationRotation[i], 0, animationRotation.length);
        }
    }
}
